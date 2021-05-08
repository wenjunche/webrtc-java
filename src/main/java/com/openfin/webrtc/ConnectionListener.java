package com.openfin.webrtc;

public interface ConnectionListener {
    public enum State {
        OPEN,
        DISCONNECTED,
        CLOSED,
        CHANNEL;
    }
    public void onStateChange(ConnectionListener.State state);
    public void onChannel(Channel channel);

}
