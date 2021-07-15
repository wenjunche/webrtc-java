/**
 * WebRTC Connection that requires ICE Servers to initiate connectivity.
 */

package com.openfin.webrtc;

import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCSdpType;
import dev.onvoid.webrtc.RTCSessionDescription;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.nonNull;

public class IceConnection extends Connection implements SignalingListener {
    private final static Logger logger = LoggerFactory.getLogger(IceConnection.class);

    private final Signaling signaling;
    private boolean peerTrickleReady;

    public IceConnection(Configuration configuration) {
        super(configuration);
        this.signaling = new Signaling(configuration);
        this.signaling.setSignalingListener(this);
        this.peerTrickleReady = false;
    }

    @Override
    public void initialize() throws Exception {
        signaling.initialize();
    }

    @Override
    protected void createPeerConnection(JSONObject rtcConfig) throws Exception {
        super.createPeerConnection(rtcConfig);
        logger.debug("emit trickle ready {}", this.configuration.getPairingCode());
        this.signaling.emit(Connection.SDPTrickleReady, this.configuration.getPairingCode());
        if (this.peerTrickleReady) {
            this.leaderOffer();
        }
    }

    private void leaderOffer() {
        if (this.signaling.isPeerLeader()) {
            try {
                this.initializeOffer();
                this.makeOffer();
            } catch (Exception ex) {
                logger.error("Error init offer", ex);
            }
        }
    }

    @Override
    protected void makeOffer() throws Exception {
        var offer = this.createOfferPayload();
        this.signaling.emit("message", offer.getJSONObject("description"));
    }

    @Override
    protected void onNewIceCandidate(RTCIceCandidate candidate) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("type", Connection.SDPCandidate);
            JSONObject candidateJson = new JSONObject();
            candidateJson.put(Connection.SDPCandidate, candidate.sdp);
            candidateJson.put("sdpMid", candidate.sdpMid);
            candidateJson.put("sdpMLineIndex", candidate.sdpMLineIndex);
            if (nonNull(candidate.serverUrl)) {
                candidateJson.put("serverUrl", candidate.serverUrl);
            }
            payload.put("candidate", candidateJson);
            this.signaling.emit("message", payload);
        } catch (Exception ex) {
            logger.error("Error sending message", ex);
        }
    }

    @Override
    protected JSONObject onOffer(JSONObject payload) {
        logger.debug("Got offer {}", payload.toString());
        JSONObject ret = new JSONObject();
        try {
            this.setRemoteDescription(new RTCSessionDescription(RTCSdpType.OFFER, payload.getString("sdp")));
            this.createAnswer();
            var description = this.peerConnection.getLocalDescription();
            JSONObject answer = new JSONObject();
            answer.put("type", Connection.SDPAnswer);
            answer.put("sdp", description.sdp);
            this.signaling.emit("message", answer);
            ret.put("status", 200);
        } catch (Exception ex) {
            logger.error("Error setRemoteDescription", ex);
            ret.put("status", 500);
            ret.put("message", ex.getMessage());
        }
        return ret;
    }


    @Override
    public void onRtcConfig(JSONObject configuration) {
        try {
            this.createPeerConnection(configuration);
        } catch (Exception ex) {
            logger.error("Error createPeerConnection", ex);
        }
    }

    @Override
    public void onSignalingOffer(JSONObject offer) {
        this.onOffer(offer);
    }

    @Override
    public void onSignalingAnswer(JSONObject answer) {
        this.onAnswer(answer);
    }

    @Override
    public void onSignalingIceCandidate(JSONObject candidate) {
        this.addIceCandidate(candidate);
    }

    @Override
    public void onSignalingTrickleReady(String code) {
        this.peerTrickleReady = true;
        if (nonNull(this.peerConnection)) {
            this.leaderOffer();
        } else {
            logger.info("signaling peerConnection not ready for trickle");
        }
    }
}
