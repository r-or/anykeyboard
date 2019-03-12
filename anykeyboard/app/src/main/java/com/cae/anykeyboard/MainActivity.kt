package com.cae.anykeyboard

import android.content.*
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import com.google.gson.*
import org.json.JSONArray
import java.lang.Exception

class MainActivity : AppCompatActivity() {

    companion object {
        val DEFAULT_SERVER_URL = "anykeyboard.com"
    }

    private val listener = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
            }
            Log.e("BroadCastReceiver", "got '$intent'")
        }
    }

    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // SERVER SPINNER
        val spinner = findViewById<Spinner>(R.id.appCompatSpinner)
        val prefs = getSharedPreferences("anykeyboard_shared", Context.MODE_PRIVATE)
        val srvList = ArrayList<String>()
        val srvListAdapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, srvList)
        val srvVals = JSONArray(prefs.getString("servers", "[\"$DEFAULT_SERVER_URL\"]"))
        try {
            Log.d("oncreate", srvVals.toString())
            for (i in 0 until srvVals.length()) {
                srvList.add(srvVals.getString(i))
            }
            srvListAdapter.notifyDataSetChanged()
            spinner.adapter = srvListAdapter
            spinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val prefEdit = prefs.edit()
                    prefEdit.putString("url", srvList[spinner.selectedItemPosition])
                    prefEdit.apply()
                    Log.d("itemSelected", "url: " + prefs.getString("url", "XXX"))
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val lastUrl = prefs.getString("url", null)
        if (lastUrl != null && lastUrl in srvList) {
            spinner.setSelection(srvList.indexOf(lastUrl))
        }

        // ADD SERVER BUTTON
        val btn1 = findViewById<Button>(R.id.button)
        btn1.setOnClickListener(object: View.OnClickListener {
            override fun onClick(v: View?) {
                val builder = AlertDialog.Builder(window.context)
                builder.setTitle("Add Server")
                val viewInflated = LayoutInflater.from(window.context).inflate(R.layout.input_txt_prompt,
                    findViewById(android.R.id.content), false)
                val txtInput = viewInflated.findViewById<EditText>(R.id.input_txt_prompt)
                builder.setView(viewInflated)

                // buttons
                builder.setPositiveButton(android.R.string.ok, object: DialogInterface.OnClickListener {
                    override fun onClick(dialog: DialogInterface?, which: Int) {
                        dialog?.dismiss()
                        val txt = txtInput.text.toString()
                        if (txt !in srvList && txt.isNotEmpty()) {
                            srvList.add(txt)
                            srvListAdapter.notifyDataSetChanged()
                            spinner.setSelection(srvList.indexOf(txt))
                            val prefEdit = prefs.edit()
                            prefEdit.putString("servers", gson.toJson(srvList))
                            prefEdit.apply()
                        }
                    }
                })
                builder.setNegativeButton(android.R.string.cancel, object: DialogInterface.OnClickListener {
                    override fun onClick(dialog: DialogInterface?, which: Int) {
                        dialog?.cancel()
                    }
                })
                builder.show()
            }
        })

        // REMOVE SERVER BUTTON
        val btn2 = findViewById<Button>(R.id.button2)
        btn2.setOnClickListener(object: View.OnClickListener {
            override fun onClick(v: View?) {
                if (spinner.selectedItem.toString() != DEFAULT_SERVER_URL) {
                    srvList.remove(spinner.selectedItem)
                    srvListAdapter.notifyDataSetChanged()
                    val prefEdit = prefs.edit()
                    prefEdit.putString("servers", gson.toJson(srvList))
                    prefEdit.apply()
                }
            }
        })
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
}