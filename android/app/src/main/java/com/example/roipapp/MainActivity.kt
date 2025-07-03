package com.example.roipapp

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.socket.client.IO
import io.socket.client.Socket

class MainActivity : AppCompatActivity() {

    private lateinit var socket: Socket

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        socket = IO.socket("https://192.168.0.105:3000")
        socket.connect()

        socket.on(Socket.EVENT_CONNECT) {
            runOnUiThread {
                println("✅ Connected to signaling server")
            }
        }

        socket.on("user-joined") { args ->
            println("New peer joined: ${args[0]}")
        }

    }
}