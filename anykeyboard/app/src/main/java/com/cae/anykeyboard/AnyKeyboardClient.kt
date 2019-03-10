package com.cae.anykeyboard

import android.content.*
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.os.Handler
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import android.view.*
import okhttp3.*
import java.io.IOException
import java.lang.Exception

class WrongUidException : Exception()

class AnyKeyboardClient : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    companion object {
        public val GIVE_SECRET = "GIVE_SECRET"
        public val ASK_SECRET = "GIVE_ME_THE_SECRET"
        public val SECRET_LENGTH = 3
    }

    private val client = OkHttpClient()
    private val keymap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
    private var url = "192.168.0.69:9080"
    private var uid: String? = null

    private var localBroadcast: LocalBroadcastManager? = null

    private var listener = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                GIVE_SECRET -> getUID(intent.getStringExtra("SECRET"))
            }
            Log.e("BroadCastReceiver", "got '$intent'")
        }
    }

    private var handler: Handler? = null

    override fun onCreateInputView(): View {
        handler = Handler()
        return getSecretKeyboard()
    }

    override fun onDestroy() {
        super.onDestroy()
        localBroadcast?.unregisterReceiver(listener)
    }

    private var secret = ""

    private fun getSecretKeyboard() : KeyboardView {
        secret = ""
        val keyboardSecretView = layoutInflater.inflate(R.layout.keyboard_view_secret,
            null) as KeyboardView
        val keyboard = Keyboard(this, R.xml.keyboard_layout_secret)
        keyboardSecretView.keyboard = keyboard
        keyboardSecretView.setOnKeyboardActionListener(this)
        return keyboardSecretView
    }

    private fun getEmptyKeyboard() : KeyboardView {
        val keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as KeyboardView
        val keyboard = Keyboard(this, R.xml.keyboard_layout)
        keyboardView.keyboard = keyboard
        return keyboardView
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        if (currentInputConnection == null)
            return

        val code = primaryCode.toChar()
        secret += code
        if (secret.length == SECRET_LENGTH) {
            getUID(secret)
            // load empty keyboard
            setInputView(getEmptyKeyboard())
        }
    }

    override fun onPress(primaryCode: Int) {}

    override fun onRelease(primaryCode: Int) {}

    override fun onText(text: CharSequence) {}

    override fun swipeLeft() {}

    override fun swipeRight() {}

    override fun swipeDown() {}

    override fun swipeUp() {}



    private fun runOnMainThread(runnable: Runnable) {
        handler?.post(runnable)
    }

    private fun getUID(secret: String) {
        var requestBody = FormBody.Builder()
            .add("secret", secret)
            .build()
        var request = Request.Builder()
            .url("http://$url/registerkb")
            .post(requestBody)
            .build()

        try {
            client.newCall(request)
                .enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e("createWebSocket", "Failed request")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        uid = response.body()?.string()
                        if (uid == null || uid?.length == 0) {
                            Log.e("getUID", "Got: '$uid'")
                            Log.e("getUID", "Failed to get UID, wrong secret?")
                            runOnMainThread(object: Runnable {
                                override fun run() {
                                    setInputView(getSecretKeyboard())
                                }
                            })
                        } else {
                            Log.i("getUID", "Retrieved UID: $uid")
                            createWebSocket()
                        }
                    }
                })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createWebSocket() {
        // get user ID
        if (uid != null) {
            var request = Request.Builder()
                .url("ws://$url/kb")
                .addHeader("uid", uid.toString())
                .build()
            val listener = WSclient()
            client.newWebSocket(request, listener)
            client.dispatcher().executorService().shutdown()
        } else {
            Log.i("createWebSocket", "Server has not replied with an user ID!")
        }
    }

    private fun handleInput(text: String) {
        try {
            if (text.length > 1) {
                val now = System.currentTimeMillis()
                var code = ""
                var prefix = ""
                // check if we have an explicit keyup/keydown
                if ('|' in text) {
                    val keys = text.split('|')
                    code = keys[keys.lastIndex]
                    prefix = keys[0]
                } else {
                    code = text
                }
                var key = 0
                when (code) {
                    "Control" -> key = KeyEvent.KEYCODE_CTRL_LEFT
                    "Alt" -> key = KeyEvent.KEYCODE_ALT_LEFT
                    "Shift" -> key = KeyEvent.KEYCODE_SHIFT_LEFT
                    "Backspace" -> key = KeyEvent.KEYCODE_DEL
                    "Delete" -> key = KeyEvent.KEYCODE_FORWARD_DEL
                    "Insert" -> key = KeyEvent.KEYCODE_INSERT
                    "Home" -> key = KeyEvent.KEYCODE_MOVE_HOME
                    "End" -> key = KeyEvent.KEYCODE_MOVE_END
                    "PageUp" -> key = KeyEvent.KEYCODE_PAGE_UP
                    "PageDown" -> key = KeyEvent.KEYCODE_PAGE_DOWN
                    "ScrollLock" -> key = KeyEvent.KEYCODE_SCROLL_LOCK
                    "CapsLock" -> key = KeyEvent.KEYCODE_CAPS_LOCK
                    "Escape" -> key = KeyEvent.KEYCODE_ESCAPE
                    "Meta" -> key = KeyEvent.KEYCODE_META_LEFT
                    "Tab" -> key = KeyEvent.KEYCODE_TAB
                    "Enter" -> key = KeyEvent.KEYCODE_ENTER
                    "ArrowUp" -> key = KeyEvent.KEYCODE_DPAD_UP
                    "ArrowDown" -> key = KeyEvent.KEYCODE_DPAD_DOWN
                    "ArrowLeft" -> key = KeyEvent.KEYCODE_DPAD_LEFT
                    "ArrowRight" -> key = KeyEvent.KEYCODE_DPAD_RIGHT
                    "F1" -> key = KeyEvent.KEYCODE_F1
                    "F2" -> key = KeyEvent.KEYCODE_F2
                    "F3" -> key = KeyEvent.KEYCODE_F3
                    "F4" -> key = KeyEvent.KEYCODE_F4
                    "F5" -> key = KeyEvent.KEYCODE_F5
                    "F6" -> key = KeyEvent.KEYCODE_F6
                    "F7" -> key = KeyEvent.KEYCODE_F7
                    "F8" -> key = KeyEvent.KEYCODE_F8
                    "F9" -> key = KeyEvent.KEYCODE_F9
                    "F10" -> key = KeyEvent.KEYCODE_F10
                    "F11" -> key = KeyEvent.KEYCODE_F11
                    "F12" -> key = KeyEvent.KEYCODE_F12
                }
                when (prefix) {
                    "D" -> currentInputConnection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0))
                    "U" -> currentInputConnection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, key, 0))
                    "" -> {
                        // we need both keyup & keydown
                        currentInputConnection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0))
                        currentInputConnection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, key, 0))
                    }
                }
            } else {
                // workaround: non-ASCII characters cannot be made into KeyEvents for some reason
                // -> could lead to problems!
                if (text.toCharArray()[0] > 127.toChar()) {
                    currentInputConnection.commitText(text, 1)
                } else {
                    var events = keymap.getEvents(text.toCharArray())
                    Log.e("anykeyboard", events.toString())
                    for (keyEvent in events) {
                        currentInputConnection.sendKeyEvent(keyEvent)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("anykeyboard", "Error trying to handle input", e)
        }

    }

    private val normalClosingStatus = 1000

    inner class WSclient : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            super.onOpen(webSocket, response)

        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            super.onMessage(webSocket, text)
            Log.i("anykeyboard", "got : \"$text\"")
            if (currentInputConnection != null) {
                handleInput(text)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            super.onClosing(webSocket, code, reason)
            webSocket.close(normalClosingStatus, null)
            Log.i("anykeyboard", "Closing: $code | $reason")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            super.onFailure(webSocket, t, response)
            Log.e("anykeyboard", t.message)
        }
    }

}
