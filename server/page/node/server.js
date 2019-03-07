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

// app.use(helmet());
app.use(cookieParser());
// custom cookie middleware
app.use(async (req, res, next) => {
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
});
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


class ConnectionInfo {
  constructor(client, secret) {
    this.wsClient = client;
    this.wsKeyboard = null;
    this.secret = secret;
  }
}

connections = [];

app.ws('/client', (wsC, req) => {
  console.log('ws connection to ' + req.cookies.anykeyboardUsr + ' established');
  const secret = Math.floor(Math.random() * (10000 - 1000) + 1000);
  conninfo = new ConnectionInfo(wsC, secret)
  connections.push(conninfo);
  wsC.send(JSON.stringify({'secret': secret}));

  wsC.on('message', (msg) => {
    console.log('client: got "' + msg + '"');
    if (conninfo.wsKeyboard != null) {
      conninfo.wsKeyboard.send(msg);
    }
  });

  wsC.on('close', (conn) => {
    console.log('ws connection to client terminated');
  })
});

app.ws('/kb', (wsK, req) => {
  connections[connections.length - 1].wsKeyboard = wsK;
  console.log('ws connection to keyboard established');
  // for (i = connections.length - 1; i >= 0; --i) {
  //   if (connections[i].wsKeyboard === null && connections[i].secret === req.secret) {
  //     connections[i].secret = null;
  //     connections[i].wsKeyboard = wsK;
  //     console.log('ws connection to keyboard established');
  //     break;
  //   }
  // }
  // if (i == -1) {
  //   // no connection with this secret found
  //   console.log('ws connection to keyboard: wrong secret!');
  //   wsK.close();
  // }

  wsK.on('close', (conn) => {
    console.log('ws connection to keyboard terminated');
  });
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
