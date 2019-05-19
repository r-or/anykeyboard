# anykeyboard
This app transmits keystrokes registered by a browser to a mobile device.

## How it works

The [app](https://play.google.com/store/apps/details?id=com.cae.anykeyboard) is installed on an Android device.
It will register as a standard Android input device which can be selected either in the settings or directly
at the soft keyboard popup.

The app is connected as a websocket client with a server.

This server is setup at [anykeyboard.com](https://anykeyboard.com). It handles the websocket connection with the 
mobile device as well as the websocket/REST connection with the browser. It will also perform the first-time
authentification to setup the connection between browser and mobile device.

Once the connections are established the server simply forwards the keystrokes from the webclient to the mobile device.
