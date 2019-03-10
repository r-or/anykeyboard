package com.cae.anykeyboard

import android.app.AlertDialog
import android.content.*
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText

class MainActivity : AppCompatActivity() {

    private var listener = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AnyKeyboardClient.ASK_SECRET -> askSecret()
            }
            Log.e("BroadCastReceiver", "got '$intent'")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(listener, IntentFilter(AnyKeyboardClient.ASK_SECRET))
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(listener)
    }

    fun askSecret() {
//        window.decorView.rootView.visibility = View.GONE
        Log.e("askSecret", "showing secret textbox")
        val i = Intent(this, MainActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        startActivity(i)
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Verification")
        val viewInflated = LayoutInflater.from(this)
            .inflate(R.layout.secret_popup, this.window.findViewById(android.R.id.keyboardView), false)
        var input = viewInflated.findViewById<EditText>(R.id.input)
        builder.setView(viewInflated)
        val parent = this
        builder.setPositiveButton(android.R.string.ok, object : DialogInterface.OnClickListener {
            override fun onClick(dialog: DialogInterface, which: Int) {
                dialog.dismiss()
                val sendSecret = Intent(AnyKeyboardClient.GIVE_SECRET)
                Log.d("askSecret", "got: " + input.text.toString())
                sendSecret.putExtra("SECRET", input.text.toString())
                LocalBroadcastManager.getInstance(parent).sendBroadcast(sendSecret)
            }
        })
        builder.setNegativeButton(android.R.string.cancel, object : DialogInterface.OnClickListener {
            override fun onClick(dialog: DialogInterface, which: Int) {
                dialog.cancel()
                parent.finish()
            }
        })
        builder.show()
    }
}
