package com.parent.monitor

import android.content.Context
import org.json.JSONObject
import org.webrtc.*

class WebRTCReceiver(
    private val context: Context,
    private val onSignal: (JSONObject) -> Unit
) {
    var onVideoTrack: ((VideoTrack) -> Unit)? = null

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var eglBase: EglBase? = null

    companion object {
        @Volatile var instance: WebRTCReceiver? = null
    }

    fun init() {
        stop()
        instance = this
        eglBase = EglBase.create()
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase!!.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase!!.eglBaseContext))
            .createPeerConnectionFactory()
    }

    fun getEglBase(): EglBase? = eglBase

    fun onOffer(sdpStr: String) {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        try { peerConnection?.close() } catch (_: Exception) {}
        peerConnection = factory?.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate ?: return
                onSignal(JSONObject().apply {
                    put("type", "ice_candidate")
                    put("role", "from_parent")
                    put("sdp_mid", candidate.sdpMid)
                    put("sdp_mline_index", candidate.sdpMLineIndex)
                    put("candidate", candidate.sdp)
                })
            }
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is VideoTrack) onVideoTrack?.invoke(track)
            }
            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack) onVideoTrack?.invoke(track)
            }
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
        }) ?: return

        // FIX: capture peerConnection in a local val before passing to observer callbacks.
        // Observer methods run on WebRTC internal threads; by the time they fire, stop()
        // may have been called and peerConnection set to null — causing NPE with !!.
        val pc = peerConnection ?: return
        val offer = SessionDescription(SessionDescription.Type.OFFER, sdpStr)
        pc.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                val pc2 = peerConnection ?: return  // re-check; stop() may have run
                pc2.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription?) {
                        sdp ?: return
                        val pc3 = peerConnection ?: return
                        pc3.setLocalDescription(object : SdpObserver {
                            override fun onSetSuccess() {
                                onSignal(JSONObject().apply {
                                    put("type", "webrtc_answer")
                                    put("sdp", sdp.description)
                                })
                            }
                            override fun onSetFailure(p0: String?) {}
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onCreateFailure(p0: String?) {}
                        }, sdp)
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, MediaConstraints())
            }
            override fun onSetFailure(p0: String?) {}
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, offer)
    }

    fun addIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        peerConnection?.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, candidate))
    }

    fun stop() {
        try { peerConnection?.close() } catch (_: Exception) {}
        try { factory?.dispose() } catch (_: Exception) {}
        try { eglBase?.release() } catch (_: Exception) {}
        peerConnection = null; factory = null; eglBase = null
        if (instance === this) instance = null
    }
}
