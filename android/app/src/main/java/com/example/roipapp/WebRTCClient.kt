package com.example.roipapp

import android.content.Context
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.CandidatePairChangeEvent
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SessionDescription

class WebRTCClient(
    private val context: Context,
    private val signalingSocket: SignalingInterface,
    private val localAudioTrackCallback: (AudioTrack) -> Unit,
    private val remoteAudioTrackCallback: (AudioTrack) -> Unit
) {
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var audioSource: AudioSource
    private lateinit var localAudioTrack: AudioTrack

    private val peers = mutableMapOf<String, PeerConnection>()

    init {
        initWebRTC()
    }

    private fun initWebRTC() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
        )

        val options = PeerConnectionFactory.Options()
        val encoderFactory = DefaultVideoEncoderFactory(
            EglBase.create().eglBaseContext, true, true
        )
        val decoderFactory = DefaultVideoDecoderFactory(EglBase.create().eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        val audioConstraints = MediaConstraints()
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)
        localAudioTrack.setEnabled(false)

        localAudioTrackCallback(localAudioTrack)
    }

    fun createPeerConnection(targetId: String): PeerConnection {
        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
                // Add TURN here later
            )
        )

        val peer =
            peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    signalingSocket.sendIceCandidate(targetId, candidate)
                }

                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate?>?) {
                    TODO("Not yet implemented")
                }

                override fun onAddStream(stream: MediaStream) {
                    stream.audioTracks.firstOrNull()?.let { remoteAudioTrackCallback(it) }
                }

                override fun onTrack(transceiver: RtpTransceiver?) {
                    transceiver?.receiver?.track()?.let {
                        if (it is AudioTrack) remoteAudioTrackCallback(it)
                    }
                }

                // Others are optional
                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {}
                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {}
                override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
                override fun onRemoveStream(mediaStream: MediaStream?) {}
                override fun onDataChannel(dc: DataChannel?) {}
                override fun onRenegotiationNeeded() {}
                override fun onStandardizedIceConnectionChange(newState: PeerConnection.IceConnectionState?) {}
                override fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent?) {}
            })!!

        // Send your local audio
        val stream = peerConnectionFactory.createLocalMediaStream("stream")
        stream.addTrack(localAudioTrack)
        peer.addStream(stream)

        peers[targetId] = peer
        return peer
    }

    fun handleRemoteOffer(from: String, offer: SessionDescription) {
        val peer = createPeerConnection(from)
        peer.setRemoteDescription(SdpObserverAdapter(), offer)
        peer.createAnswer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    peer.setLocalDescription(SdpObserverAdapter(), it)
                    signalingSocket.sendAnswer(from, it)
                }
            }
        }, MediaConstraints())
    }

    fun handleRemoteAnswer(from: String, answer: SessionDescription) {
        peers[from]?.setRemoteDescription(SdpObserverAdapter(), answer)
    }

    fun handleRemoteIceCandidate(from: String, candidate: IceCandidate) {
        peers[from]?.addIceCandidate(candidate)
    }

    fun setMicEnabled(enabled: Boolean) {
        localAudioTrack.setEnabled(enabled)
    }
}
