/**
 * Configuration for creating WebRTC connections
 */
package com.openfin.webrtc;

import com.openfin.desktop.DesktopConnection;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Configuration {
    private final static Logger logger = LoggerFactory.getLogger(Configuration.class);

    private String signalingBaseUrl;
    private String pairingCode;
    private DesktopConnection desktopConnection;
    private JSONObject webRTCConfiguration;  // https://developer.mozilla.org/en-US/docs/Web/API/RTCConfiguration/iceServers

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

    public DesktopConnection getDesktopConnection() {
        return desktopConnection;
    }

    public void setDesktopConnection(DesktopConnection desktopConnection) {
        this.desktopConnection = desktopConnection;
    }
}
