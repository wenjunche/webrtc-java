package com.openfin.webrtc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Configuration {
    private final static Logger logger = LoggerFactory.getLogger(Configuration.class);

    private String signalingBaseUrl;
    private String pairingCode;

    public String getSignalingBaseUrl() {
        return signalingBaseUrl;
    }

    public void setSignalingBaseUrl(String signalingBaseUrl) {
        this.signalingBaseUrl = signalingBaseUrl;
    }

    public String getPairingCode() {
        return pairingCode;
    }

    public void setPairingCode(String pairingCode) {
        this.pairingCode = pairingCode;
    }
}
