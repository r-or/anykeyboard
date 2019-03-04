package com.cae.anykeyboard

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.Log
import android.view.View
import okhttp3.*

class AnyKeyboardClient : InputMethodService() {

    private val client = OkHttpClient()

    override fun onCreateInputView(): View {
        val keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as KeyboardView
        val keyboard = Keyboard(this, R.xml.keyboard_layout)
        keyboardView.keyboard = keyboard
        createWebSocket()

        return keyboardView
    }

    private fun createWebSocket() {
        val request = Request.Builder()
            .url("ws://192.168.0.69:9080/kb")
            .addHeader("type", "keyboard")
            .build()
        val listener = WSclient()
        client.newWebSocket(request, listener)
        client.dispatcher().executorService().shutdown()
    }

    private val normalClosingStatus = 1000

    inner class WSclient : WebSocketListener() {

        override fun onMessage(webSocket: WebSocket, text: String) {
            super.onMessage(webSocket, text)
            Log.d("anykeyboard", "got : \"$text\"")
            if (currentInputConnection != null) {
                currentInputConnection.commitText(text, 1)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            super.onClosing(webSocket, code, reason)
            webSocket.close(normalClosingStatus, null)
            Log.d("anykeyboard", "Closing: $code | $reason")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            super.onFailure(webSocket, t, response)
            Log.d("anykeyboard", t.message)
        }
    }

}
