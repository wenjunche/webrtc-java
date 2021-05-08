package com.openfin.webrtc;

import dev.onvoid.webrtc.RTCDataChannel;
import dev.onvoid.webrtc.RTCDataChannelBuffer;
import dev.onvoid.webrtc.RTCDataChannelObserver;
import dev.onvoid.webrtc.RTCDataChannelState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.util.Objects.nonNull;

public class Channel implements RTCDataChannelObserver {
    private final static Logger logger = LoggerFactory.getLogger(Channel.class);
    private RTCDataChannel dataChannel;
    private CopyOnWriteArrayList<ChannelListener> channelListeners;

    public Channel(RTCDataChannel dataChannel) {
        this.dataChannel = dataChannel;
        this.dataChannel.registerObserver(this);
        this.channelListeners = new CopyOnWriteArrayList<>();
    }

    public String getName() {
        return this.dataChannel.getLabel();
    }

    public boolean addChannelListener(ChannelListener listener) {
        return this.channelListeners.add(listener);
    }

    public boolean removeChannelListener(ChannelListener listener) {
        return this.channelListeners.remove(listener);
    }

    private void fireChannelStatusEvent() {
        var state = this.mapState(this.dataChannel.getState());
        if (nonNull(state)) {
            for (ChannelListener listener : this.channelListeners) {
                listener.onStateChange(state);
            }
        }
    }

    private void fireChannelMessageEvent(String message) {
        for (ChannelListener listener : this.channelListeners) {
            listener.onMessage(message);
        }
    }

    private ChannelListener.State mapState(RTCDataChannelState state) {
        if (state == RTCDataChannelState.OPEN) {
            return ChannelListener.State.OPEN;
        }
        if (state == RTCDataChannelState.CLOSED) {
            return ChannelListener.State.CLOSED;
        }
        return null;
    }

    @Override
    public void onBufferedAmountChange(long previousAmount) {
    }

    @Override
    public void onStateChange() {
        logger.debug("onStateChange {}", this.dataChannel.getState().toString());
        this.fireChannelStatusEvent();
    }

    @Override
    public void onMessage(RTCDataChannelBuffer buffer) {
        String m = decodeMessage(buffer);
        this.fireChannelMessageEvent(m);
    }

    private String decodeMessage(RTCDataChannelBuffer buffer) {
        ByteBuffer byteBuffer = buffer.data;
        byte[] payload;
        if (byteBuffer.hasArray()) {
            payload = byteBuffer.array();
        }
        else {
            payload = new byte[byteBuffer.limit()];
            byteBuffer.get(payload);
        }
        return new String(payload, StandardCharsets.UTF_8);
    }

}
