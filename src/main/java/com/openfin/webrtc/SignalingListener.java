package com.openfin.webrtc;

import org.json.JSONObject;

public interface SignalingListener {
    public void onRtcConfig(JSONObject configuration);
    public void onSignalingOffer(JSONObject offer);
    public void onSignalingAnswer(JSONObject answer);
    public void onSignalingIceCandidate(JSONObject candidate);
    public void onSignalingTrickleReady(String code);
}
