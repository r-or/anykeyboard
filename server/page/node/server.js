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
  rclient.set('pep8request-counter', 0);
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

// app.use(helmet());
app.use(cookieParser());
// cookies
// app.use((req, res, next) => {
//   var cookie = req.cookies.cookieName;
//   if (cookie === undefined) {
//     // user doesn't exist
//     while (true) {
//       const sha = crypto.createHash('sha256');
//       sha.update(Math.random().toString());
//       var uid = 'user:' + sha.digest('hex');
//       console.log(uid);
//       console.log(rclient.exists(uid));
//       break;
//       if (!rclient.exists(uid)) {
//         console.log(rclient.hset(uid, '{}'));
//         break;
//       }
//     }
//     console.log('New user uid:', uid);
//   } else {
//     // user exists
//     console.log('User exists!');
//   }
//   next();
// });
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

app.ws('/client', (wsC, req) => {
  console.log('ws connection to client established');
  rclient.lpush({'wsC': wsC, 'id': clients.length})

  wsC.on('message', (msg) => {
    console.log('client: got "' + msg + '"');
    keyboards[0].send(msg);
  });

  wsC.on('close', (conn) => {
    console.log('ws connection to client terminated');
  })
});

app.ws('/kb', (wsK, req) => {
  console.log('ws connection to keyboard established');
  keyboards.push(wsK)
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
