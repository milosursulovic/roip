package com.example.roipapp

import io.socket.client.Socket
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

open class SignalingInterface(
    private val socket: Socket,
    private val myId: String,
    private val onRemoteOffer: (String, SessionDescription) -> Unit,
    private val onRemoteAnswer: (String, SessionDescription) -> Unit,
    private val onRemoteCandidate: (String, IceCandidate) -> Unit,
    private val onUserJoined: (String) -> Unit,
    private val onUserLeft: (String) -> Unit
) {

    init {
        setupListeners()
    }

    private fun setupListeners() {
        socket.on("user-joined") { args ->
            val userId = args[0] as String
            if (userId != myId) onUserJoined(userId)
        }

        socket.on("offer") { args ->
            val data = args[0] as JSONObject
            val from = data.getString("from")
            val sdp = data.getJSONObject("offer")
            val desc = SessionDescription(SessionDescription.Type.OFFER, sdp.getString("sdp"))
            onRemoteOffer(from, desc)
        }

        socket.on("answer") { args ->
            val data = args[0] as JSONObject
            val from = data.getString("from")
            val sdp = data.getJSONObject("answer")
            val desc = SessionDescription(SessionDescription.Type.ANSWER, sdp.getString("sdp"))
            onRemoteAnswer(from, desc)
        }

        socket.on("ice-candidate") { args ->
            val data = args[0] as JSONObject
            val from = data.getString("from")
            val candidate = IceCandidate(
                data.getString("sdpMid"),
                data.getInt("sdpMLineIndex"),
                data.getString("candidate")
            )
            onRemoteCandidate(from, candidate)
        }

        socket.on("user-left") { args ->
            val userId = args[0] as String
            onUserLeft(userId)
        }
    }

    fun sendOffer(target: String, offer: SessionDescription) {
        val payload = JSONObject().apply {
            put("target", target)
            put("offer", JSONObject().apply {
                put("type", offer.type.canonicalForm())
                put("sdp", offer.description)
            })
        }
        socket.emit("offer", payload)
    }

    fun sendAnswer(target: String, answer: SessionDescription) {
        val payload = JSONObject().apply {
            put("target", target)
            put("answer", JSONObject().apply {
                put("type", answer.type.canonicalForm())
                put("sdp", answer.description)
            })
        }
        socket.emit("answer", payload)
    }

    fun sendIceCandidate(target: String, candidate: IceCandidate) {
        val payload = JSONObject().apply {
            put("target", target)
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
            put("candidate", candidate.sdp)
        }
        socket.emit("ice-candidate", payload)
    }
}
