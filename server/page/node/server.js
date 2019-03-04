const express = require('express');
const path = require('path');
const bodyParser = require('body-parser');
const redis = require('redis');
const fs = require('fs');
const enableWs = require('express-ws')

// const https = require('https');
// const helmet = require('helmet');

const app = express();
enableWs(app);
// const rclient = redis.createClient({host: 'redis', port: 6379});
// rclient.on('connect', () => {
//   console.log('Connected to redis!');
//   rclient.set('pep8request-counter', 0);
// });
// rclient.on('error', (err) => {
//   console.log('Error connecting to redis: ' + err);
// });

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
      // rclient.incr('page-counter', (err, cnt) => {
      //   console.log('Page count: ' + cnt.toString());
      // });
    }
  });
});

// explicit router for cert challenge -- TODO
/*app.get('/.well-known/acme-challenge*', (req, res) => {
  res.sendFile(req.url, staticfileoptions, (err) => {
    if (err) {
      console.log("Error while trying acme-challenge:");
      console.log(err);
      res.status(err.status).end();
    }
  });
});*/

// app.post('/2pep', (req, res) => {
//   rclient.incr('pep8request-counter', (err, uniqueID) => {
//     const rSubClient = redis.createClient({host: 'redis', port: 6379});
//     rSubClient.subscribe('pep8result#' + uniqueID.toString());
//     rSubClient.on('message', (chan, msg) => {
//         var job = JSON.parse(msg);
//         console.log('Job #' + uniqueID.toString() + ' done!');
//         res.send(msg);
//       });
//     rSubClient.on('connect', () => {
//       rclient.lpush('pep8jobs',
//         JSON.stringify({'id': uniqueID,
//                         'lines': req.body.txt,
//                         'select': req.body.sel
//       }));
//       console.log('Submitted job #' + uniqueID.toString() + '!');
//     });
//     rSubClient.on('error', (err) => {
//       console.log('Subclient #' + uniqueID.toString() + ': error connecting to redis: ' + err);
//     });
//   });
// })

var clients = [];
var keyboards = [];

app.ws('/client', (wsC, req) => {
  console.log('ws connection to client established');
  clients.push({'wsC': wsC, 'id': clients.length})
  wsC.on('message', (msg) => {
    console.log('client: got "' + msg.utf8Data + '"');
    keyboards[0].send(msg.utf8Data);
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
