package com.example.roipapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.socket.client.IO
import io.socket.client.Socket
import org.webrtc.MediaConstraints
import org.webrtc.SessionDescription
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var socket: Socket
    private var isTalking = false
    private var audioThread: Thread? = null
    private val host = Constants.API_URL
    private var audioTrack: AudioTrack? = null

    private lateinit var signaling: SignalingInterface
    private lateinit var rtcClient: WebRTCClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = getSharedPreferences("app", MODE_PRIVATE).getString("token", null)
        if (token == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (checkMicPermission()) {
            initializeUI()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initializeUI() {
        val joinBtn = findViewById<Button>(R.id.joinButton)
        val pttBtn = findViewById<Button>(R.id.pttButton)
        val statusText = findViewById<TextView>(R.id.statusText)

        val opts = IO.Options().apply {
            transports = arrayOf("polling")
            reconnection = true
            forceNew = true
        }

        socket = IO.socket(host, opts)
        socket.connect()

        socket.on(Socket.EVENT_CONNECT) {
            runOnUiThread {
                statusText.text = "Connected"
            }
        }

        signaling = SignalingInterface(
            socket = socket,
            myId = "",
            onRemoteOffer = { _, _ -> },
            onRemoteAnswer = { _, _ -> },
            onRemoteCandidate = { _, _ -> },
            onUserJoined = {},
            onUserLeft = {}
        )

        rtcClient = WebRTCClient(
            context = this,
            signalingSocket = signaling,
            localAudioTrackCallback = { },
            remoteAudioTrackCallback = { track -> track.setEnabled(true) }
        )

        signaling = SignalingInterface(
            socket = socket,
            myId = socket.id(),
            onRemoteOffer = { from, offer -> rtcClient.handleRemoteOffer(from, offer) },
            onRemoteAnswer = { from, answer -> rtcClient.handleRemoteAnswer(from, answer) },
            onRemoteCandidate = { from, candidate ->
                rtcClient.handleRemoteIceCandidate(
                    from,
                    candidate
                )
            },
            onUserJoined = { userId ->
                val pc = rtcClient.createPeerConnection(userId)
                pc.createOffer(object : SdpObserverAdapter() {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        desc?.let {
                            pc.setLocalDescription(SdpObserverAdapter(), it)
                            signaling.sendOffer(userId, it)
                        }
                    }
                }, MediaConstraints())
            },
            onUserLeft = { }
        )

        socket.on(Socket.EVENT_CONNECT_ERROR) { args ->
            runOnUiThread {
                statusText.text = "Connect Error: ${args.firstOrNull()}"
            }
        }

        socket.on(Socket.EVENT_DISCONNECT) {
            runOnUiThread {
                statusText.text = "Disconnected"
            }
        }

        socket.on("audio-data") { args ->
            val data = args[0] as ByteArray
            audioTrack?.write(data, 0, data.size)
        }

        joinBtn.setOnClickListener {
            socket.emit("join", "test channel")
            startPlayback()
        }

        pttBtn.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                startRecording()
                pttBtn.text = "🎙️ Talking..."
            } else if (event.action == MotionEvent.ACTION_UP) {
                stopRecording()
                pttBtn.text = "🎤 Push to Talk"
            }
            true
        }
    }


    private fun checkMicPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initializeUI()
        } else {
            Toast.makeText(this, "Mic permission required to use Push-to-Talk", Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun startRecording() {
        if (!checkMicPermission()) {
            Toast.makeText(this, "Microphone permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        isTalking = true
        audioThread = thread(start = true) {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                val buffer = ByteArray(bufferSize)
                recorder.startRecording()

                while (isTalking) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        socket.emit("audio-data", buffer.copyOf(read))
                    }
                }

                recorder.stop()
                recorder.release()
            } catch (e: SecurityException) {
                runOnUiThread {
                    Toast.makeText(this, "Permission denied for microphone", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    private fun stopRecording() {
        isTalking = false
        audioThread?.join()
        audioThread = null
    }

    private fun startPlayback() {
        val bufferSize = AudioTrack.getMinBufferSize(
            16000,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            16000,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
            AudioTrack.MODE_STREAM
        )
        audioTrack?.play()
    }

    private fun stopPlayback() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::socket.isInitialized) {
            socket.disconnect()
            socket.off()
        }
    }
}
