const express = require('express');
const path = require('path');
const bodyParser = require('body-parser');
const cookieParser = require('cookie-parser')
const redis = require('redis');
const fs = require('fs');
const enableWs = require('express-ws')
const crypto = require('crypto')

// const https = require('https');
// const helmet = require('helmet');

const app = express();
enableWs(app);
const rclient = redis.createClient({host: 'localhost', port: 6379});
rclient.on('connect', () => {
  console.log('Connected to redis!');
  rclient.set('request-counter', 0);
});
rclient.on('error', (err) => {
  console.log('Error connecting to redis: ' + err);
});

// certs
// const credentials = {
//   key: fs.readFileSync('/certs/live/pep8format.com/privkey.pem', 'utf8'),
//   ca: fs.readFileSync('/certs/live/pep8format.com/chain.pem', 'utf8'),
//   cert: fs.readFileSync('/certs/live/pep8format.com/cert.pem', 'utf8')
// };

// static files
const staticfileoptions = {
  root: path.join(__dirname, '..', 'public/'),
  dotfiles: 'allow'
};

// cookie settings
const cookieoptions = {
  maxAge: 500000,
  httpOnly: true
}

// create new user
function genUser(res) {
  return new Promise((resolve, reject) => {
    const sha = crypto.createHash('sha256');
    sha.update(Math.random().toString());
    var uid = 'user:' + sha.digest('hex');
    rclient.hsetnx(uid, 'keyboardConn', '', (err, success) => {
      if (!err) {
        if (success) {
          console.log('New user uid:', uid);
          resolve(uid);
        }
      } else {
        reject(err);
      }
    });
  });
}

const checkUIDcookie = async (req, res, next) => {
  var cookie = req.cookies.anykeyboardUsr;
  if (cookie === undefined) {
    // user doesn't exist
    var uid = await genUser();
    res.cookie('anykeyboardUsr', uid, cookieoptions);
    next();
  } else {
    // user should exist
    rclient.exists(cookie, async (err, rexists) => {
      if (!rexists) {
        var uid = await genUser(res);
        res.cookie('anykeyboardUsr', uid, cookieoptions)
      } else {
        console.log('User exists:', cookie);
      }
      next();
    });
  }
}


// app.use(helmet());
app.use(cookieParser());
// custom cookie middleware
app.use('/user/id', checkUIDcookie);
app.use(bodyParser.urlencoded({extended: false}));
app.use(bodyParser.json({limit: '5mb'}));
app.use(express.static(staticfileoptions['root'], staticfileoptions));

// root page
app.get('/', (req, res) => {
  res.sendFile('index.html', staticfileoptions, (err) => {
    if (err) {
      console.log(err);
      res.status(err.status).end();
    } else {
      rclient.incr('page-counter', (err, cnt) => {
        console.log('Page count: ' + cnt.toString());
      });
    }
  });
});

// user id request
app.get('/user/id', (req, res) => {
  res.end();
});


const genSecret = () => {
  return Math.floor(Math.random() * (10000 - 1000) + 1000);
}


// keys: uid; values: ConnectionInfo
var connections = {};
var danglingConnections = [];

class ConnectionInfo {
  constructor(uid, client=null, keyboard=null) {
    this.uid = uid;
    this.wsClient = client;
    this.wsKeyboard = keyboard;
    this.secret = genSecret();
    danglingConnections.push(this);
    this.connected = false;
  }
  addMissingClient(client) {
    this.wsClient = client;
    danglingConnections.splice(danglingConnections.indexOf(this));
    this.connected = true;
  }
  addMissingKeyboard(keyboard) {
    this.wsKeyboard = keyboard;
    danglingConnections.splice(danglingConnections.indexOf(this));
    this.connected = true;
  }
}

/** CLIENT CONNECTION **/
const statusStr = (status) => {
  return JSON.stringify({'status': status})
}

app.ws('/client', (wsC, req) => {
  var uid = req.cookies.anykeyboardUsr
  console.log('ws connection to ' + uid + ' established');
  if (!(uid in connections)) {
    connections[uid] = new ConnectionInfo(uid, wsC, null);
  } else if (!connections[uid].connected) {
      connections[uid].addMissingClient(wsC);
  }
  wsC.send(statusStr('Your secret: ' + connections[uid].secret));

  wsC.on('message', (msg) => {
    console.log('client: got "' + msg + '"');
    if (connections[uid].wsKeyboard != null) {
      connections[uid].wsKeyboard.send(msg);
    }
  });
  wsC.on('close', (conn) => {
    console.log('ws connection to client terminated');
  })
});

//someone doesn't want to communicate with websockets
app.post('/rclient', (req, res) => {
  console.log('client REST: got "' + req.body.key + '"');
  var uid = req.cookies.anykeyboardUsr;
  if (!(uid in connections)) {
    connections[uid] = new ConnectionInfo(uid, null, null);
  } else if (!connections[uid].connected) {
    connections[uid].addMissingClient(null);
  }
  if (connections[uid].wsKeyboard != null) {
    connections[uid].wsKeyboard.send(req.body.key);
    res.end(statusStr("Status: connected"));
    return
  }

  res.end(statusStr('Your secret: ' + connections[uid].secret));
});


/** KEYBOARD CONNECTION (always websocket) **/
// TODO: send POST request to get user ID (if secret is correct!)

app.post('/registerkb', (req, res) => {
  console.log('trying to register keyboard; conns:', danglingConnections.length);
  var secret = parseInt(req.body.secret)
  for (i = 0; i < danglingConnections.length; ++i) {
    console.log(i, secret, danglingConnections[i].secret);
    if (secret === danglingConnections[i].secret) {
      console.log('sending UID...')
      res.send(danglingConnections[i].uid);
      return
    }
  }
  res.end()
});

app.ws('/kb', (wsK, req) => {
  var uid = req.headers.uid;
  if (uid === undefined || !(uid in connections)) {
    // there is no user connected yet: close connection
    console.log('ws connection to keyboard denied');
    wsK.close();
  } else if (!connections[uid].connected) {
    connections[uid].addMissingKeyboard(wsK);
    console.log('ws connection to keyboard established');
    if (connections[uid].wsClient != null)
      connections[uid].wsClient.send(statusStr('Status: connected'));

    var gotPong = true;
    pingpong = setInterval(() => {
      if (!gotPong) {
        console.log("keyboard: didn't get PONG! Terminating connection...");
        clearInterval(pingpong);
        wsK.close();
        return;
      }
      gotPong = false;
      wsK.send("PING");
    }, 10000);

    wsK.on('message'), (msg) => {
      console.log('keyboard: got', msg);
      if (msg == "PONG") {
        gotPong = true;
      }
    }

    wsK.on('close', (conn) => {
      console.log('ws connection to keyboard terminated');
      connections[uid].wsKeyboard = null;
      connections[uid].connected = false;
      if (connections[uid].wsClient != null)
        connections[uid].wsClient.send(statusStr('Status: phone disconnected'));
    });
  }
});

app.use((req, res, next) => {
  res.status(404).end();
})

server = app.listen(9080, () => {
  console.log('HTTP listening on port 9080...')
});
// https.createServer(credentials, app).listen(9443, () => {
//   console.log('HTTPS listening on port 9443...');
// });
