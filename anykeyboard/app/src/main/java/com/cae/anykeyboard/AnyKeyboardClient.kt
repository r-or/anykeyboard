package com.cae.anykeyboard

import android.content.*
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.os.Handler
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import android.view.*
import android.support.v7.widget.Toolbar
import android.widget.Button
import okhttp3.*
import java.io.IOException
import java.lang.Exception

class AnyKeyboardClient : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    companion object {
        // used for local broadcast
        const val GIVE_SECRET = "GIVE_SECRET"
        const val ASK_SECRET = "GIVE_ME_THE_SECRET"
        const val SECRET_LENGTH = 4
    }

    // connection
    private var client: OkHttpClient? = null
    private var ws: okhttp3.WebSocket? = null
    private var uid: String? = null
    private var secret = ""
    private val wsNormalClosingStatus = 1000

    // keyboard
    private val keymap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
    private var cViewTbar: Toolbar? = null
    private lateinit var cView: View
    private var initialCview = true

    enum class ToolbarType {
        NORMAL, RETRY
    }
    private var tbType = ToolbarType.NORMAL


    // local broadcast
    private var localBroadcast: LocalBroadcastManager? = null
    private var listener = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                GIVE_SECRET -> {
                    secret = intent.getStringExtra("SECRET")
                    getUID()
                }
            }
            Log.e("BroadCastReceiver", "got '$intent'")
        }
    }

    // various
    private var handler: Handler? = null
    private var prefs: SharedPreferences ? = null


    override fun onCreateInputView(): View {
        client = OkHttpClient()
        handler = Handler()
        prefs = getSharedPreferences("anykeyboard_shared", Context.MODE_PRIVATE)
        return getSecretKeyboard()
    }

    override fun onCreateCandidatesView(): View {
        cView = layoutInflater.inflate(R.layout.keyboard_info, null)
        cViewTbar = cView.findViewById(R.id.le_toolbar)
        if (initialCview) {
            cViewTbar?.title = getString(R.string.input_secret)
            initialCview = false
        }
        return cView
    }

    override fun onDestroy() {
        super.onDestroy()
        localBroadcast?.unregisterReceiver(listener)
        ws?.close(wsNormalClosingStatus, null)
        Log.i("onDestroy", "destroying...")
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        if (currentInputConnection == null)
            return

        val code = primaryCode.toChar()
        secret += code
        if (secret.length == SECRET_LENGTH) {
            getUID()
            // load empty keyboard
            setInputView(getEmptyKeyboard())
        }
        cViewTbar?.title = getString(R.string.input_secret) + " " + secret
    }

    override fun onPress(primaryCode: Int) {}

    override fun onRelease(primaryCode: Int) {}

    override fun onText(text: CharSequence) {}

    override fun swipeLeft() {}

    override fun swipeRight() {}

    override fun swipeDown() {}

    override fun swipeUp() {}

    private fun tryConnection() {
        if (uid != null) {
            createWebSocket()
        } else if (secret.length == SECRET_LENGTH) {
            getUID()
        } else {
            setInputView(getSecretKeyboard())
            setStatus(getString(R.string.retry))
        }
    }

    private fun setToolbar(type: ToolbarType) {
        if (tbType == type)
            return

        cViewTbar?.removeAllViews()
        Log.i("setToolbar", "removed views")
        when (type) {
            ToolbarType.NORMAL -> {

            }
            ToolbarType.RETRY -> {
                val b = Button(cView.context)
                val lp = Toolbar.LayoutParams(
                    Toolbar.LayoutParams.WRAP_CONTENT, Toolbar.LayoutParams.MATCH_PARENT)
                lp.gravity = Gravity.END
                b.layoutParams = lp
                b.setText(R.string.btn_retry)
                b.setOnClickListener {
                    setInputView(getSecretKeyboard())
                    setStatus(getString(R.string.input_secret))
                }
                cViewTbar?.addView(b)
            }
        }

        tbType = type
    }

    private fun setStatus(text: String, toolbarType: ToolbarType = ToolbarType.NORMAL) {
        setToolbar(toolbarType)
        cViewTbar?.title = text
        setCandidatesViewShown(true)
    }

    private fun getSecretKeyboard() : KeyboardView {
        secret = ""
        val keyboardSecretView = layoutInflater.inflate(R.layout.keyboard_view_secret,
            null) as KeyboardView
        val keyboard = Keyboard(this, R.xml.keyboard_layout_secret)
        keyboardSecretView.keyboard = keyboard
        keyboardSecretView.setOnKeyboardActionListener(this)
        setCandidatesViewShown(true)

        return keyboardSecretView
    }

    private fun getEmptyKeyboard() : KeyboardView {
        val keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as KeyboardView
        val keyboard = Keyboard(this, R.xml.keyboard_layout)
        keyboardView.keyboard = keyboard
        //setCandidatesViewShown(false)
        return keyboardView
    }

    private fun runOnMainThread(runnable: Runnable) {
        handler?.post(runnable)
    }

    private fun getUID() {
        val url = prefs?.getString("url", "")
        val requestBody = FormBody.Builder()
            .add("secret", secret)
            .build()
        val request = Request.Builder()
            .url("http://$url/registerkb")
            .post(requestBody)
            .build()

        try {
            client?.newCall(request)
                ?.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        runOnMainThread(Runnable {
                            setStatus(getString(R.string.conn_failed), ToolbarType.RETRY)
                        })
                        Log.e("createWebSocket", "Failed request: $e")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        uid = response.body()?.string()
                        if (uid == null || uid?.length == 0) {
                            Log.e("getUID", "Got: '$uid'")
                            Log.e("getUID", "Failed to get UID, wrong secret?")
                            runOnMainThread(Runnable {
                                setInputView(getSecretKeyboard())
                                setStatus(getString(R.string.retry))
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
        val url = prefs?.getString("url", "")
        val dbg = prefs?.getBoolean("debug", false)
        val proto = if (dbg != null && dbg == true) "ws:" else "wss:"
        // get user ID
        if (uid != null) {
            val request = Request.Builder()
                .url("$proto//$url/kb")
                .addHeader("uid", uid.toString())
                .build()
            val wsListener = WSclient()
            ws = client?.newWebSocket(request, wsListener)
//            client?.dispatcher()?.executorService()?.shutdown()
        } else {
            setStatus(getString(R.string.conn_issue))
            Log.e("createWebSocket", "Server has not replied with an user ID!")
        }
    }

    private fun handleSocketInput(text: String) {
        setCandidatesViewShown(false)
        try {
            if (text.length > 1) {
                val now = System.currentTimeMillis()
                val code: String
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
                    val events = keymap.getEvents(text.toCharArray())
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

    inner class WSclient : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            super.onOpen(webSocket, response)
            runOnMainThread(Runnable {
                setStatus(getString(R.string.connected))
            })
            Log.i("ws:onOpen", "connected!")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            super.onMessage(webSocket, text)
            Log.i("ws:onMessage", "got: \"$text\"")
            if (text == "PING") {
                Log.i("ws:onMessage", "send PONG")
                webSocket.send("PONG")
            } else {
                if (currentInputConnection != null) {
                    handleSocketInput(text)
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            super.onClosing(webSocket, code, reason)
            webSocket.close(wsNormalClosingStatus, null)
            runOnMainThread(Runnable {
                setStatus(getString(R.string.disconnected), ToolbarType.RETRY)
            })
//            Log.i("ws:onClosing", "Closing: $code | $reason")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            super.onFailure(webSocket, t, response)
            runOnMainThread(Runnable {
                setStatus(getString(R.string.conn_issue), ToolbarType.RETRY)
                webSocket.cancel()
            })
            Log.e("ws:onFailure", "Failure: " + t.message.toString())
        }
    }

}
