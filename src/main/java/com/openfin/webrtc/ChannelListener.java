/**
 * Listener for channel events
 */
package com.openfin.webrtc;

public interface ChannelListener {
    public enum State {
        OPEN,
        CLOSED;
    }
    public void onStateChange(State state);
    public void onMessage(String message);
}
