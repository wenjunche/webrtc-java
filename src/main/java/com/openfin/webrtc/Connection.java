/**
 * Abstract class for WebRTC Connection
 */
package com.openfin.webrtc;

import dev.onvoid.webrtc.*;
import dev.onvoid.webrtc.media.MediaStream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import static java.util.Objects.nonNull;

public abstract class Connection implements PeerConnectionObserver {
    private final static Logger logger = LoggerFactory.getLogger(Connection.class);
    private final CountDownLatch connectedLatch;

    public static final String SDPAnswer = "answer";
    public static final String SDPOffer = "offer";
    public static final String SDPCandidate = "candidate";

    protected final Configuration configuration;
    private final PeerConnectionFactory factory;
    protected RTCPeerConnection peerConnection;
    private Channel defaultChannel;
    private String defaultChannelName;  // name of default DataChannel
    private Map<String, Channel> channelMap;

    private CopyOnWriteArrayList<ConnectionListener> connectionListeners;

    public Connection(Configuration configuration) {
        this.configuration = configuration;
        this.defaultChannelName = String.format("%s:default", configuration.getPairingCode());
        this.factory = new PeerConnectionFactory();
        this.connectedLatch = new CountDownLatch(1);
        this.channelMap = new ConcurrentHashMap<>();
        this.connectionListeners = new CopyOnWriteArrayList<>();
        logger.debug("Created Connection with default channel {}", this.defaultChannelName);
    }

    abstract public void initialize() throws Exception;

    protected void createPeerConnection(JSONObject rtcConfig) {
        RTCConfiguration config = createRTCConfig(rtcConfig);
        this.peerConnection = factory.createPeerConnection(config, this);
    }

    private RTCConfiguration createRTCConfig(JSONObject rtcConfig) {
        RTCConfiguration config = new RTCConfiguration();
        if (nonNull(rtcConfig)) {
            JSONArray array = rtcConfig.getJSONArray("iceServers");
            for (int i = 0; i < array.length(); i++) {
                RTCIceServer iceServer = new RTCIceServer();
                JSONObject iceJson = array.getJSONObject(i);
                JSONArray urls = iceJson.optJSONArray("urls");
                if (nonNull(urls)) {
                    for (Object url: urls) {
                        iceServer.urls.add(url.toString());
                    }
                } else {
                    iceServer.urls.add(iceJson.optString("urls"));
                }
                config.iceServers.add(iceServer);
                logger.debug("adding ice server: {}", iceServer);
            }
        }
        return config;
    }

    /**
     * Initializes offer-answer flow.  Only one party of the peer-peer should call this
     * @throws Exception
     */
    public void initializeOffer() throws Exception {
        logger.debug("Initialize offer {}", this.configuration.getPairingCode());
        this.createDefaultChannel();
        this.createOffer();
    }

    /**
     *  need to create a data channel to trigger Trickle ICE process
     */
    private void createDefaultChannel() {
        var defaultDataChannel = this.peerConnection.createDataChannel(this.defaultChannelName, new RTCDataChannelInit());
        this.defaultChannel = new Channel(defaultDataChannel);
    }

    /**
     * set local description to OFFER
     *
     * @throws Exception
     */
    private void createOffer() throws Exception {
        CreateDescObserver createObserver = new CreateDescObserver();
        SetDescObserver setObserver = new SetDescObserver();
        peerConnection.createOffer(new RTCOfferOptions(), createObserver);
        RTCSessionDescription offerDesc = createObserver.get();
        peerConnection.setLocalDescription(offerDesc, setObserver);
        setObserver.get();
    }

    /**
     * Send OFFER to the other peer
     *
     * @throws Exception
     */
    protected abstract void makeOffer() throws Exception;

    protected JSONObject createOfferPayload() {
        var offer = this.peerConnection.getLocalDescription();
        JSONObject description = new JSONObject();
        description.put("type", SDPOffer);
        description.put("sdp", offer.sdp);
        JSONObject payload = new JSONObject();
        payload.put("description", description);
        logger.debug("last ICE candidate {}", payload.toString());
        return payload;
    }

    /**
     * set local description to ANSWER
     *
     * @throws Exception
     */
    protected RTCSessionDescription createAnswer() throws Exception {
        CreateDescObserver createObserver = new CreateDescObserver();
        SetDescObserver setObserver = new SetDescObserver();
        peerConnection.createAnswer(new RTCAnswerOptions(), createObserver);
        RTCSessionDescription answerDesc = createObserver.get();
        peerConnection.setLocalDescription(answerDesc, setObserver);
        setObserver.get();
        return answerDesc;
    }

    /**
     * Process ANSWER from the peer
     * @param payload
     * @return
     */
    protected JSONObject onAnswer(JSONObject payload) {
        logger.debug("Got answer {}", payload.toString());
        JSONObject ret = new JSONObject();
        try {
            this.setRemoteDescription(new RTCSessionDescription(RTCSdpType.ANSWER, payload.getString("sdp")));
            ret.put("status", 200);
        } catch (Exception ex) {
            logger.error("Error setRemoteDescription", ex);
            ret.put("status", 500);
            ret.put("message", ex.getMessage());
        }
        return ret;
    }

    /**
     * Porcess OFFER from the peer
     * @param payload
     * @return
     */
    protected JSONObject onOffer(JSONObject payload) {
        return null;
    }

    protected void setRemoteDescription(RTCSessionDescription description) throws Exception {
        SetDescObserver setObserver = new SetDescObserver();
        peerConnection.setRemoteDescription(description, setObserver);
        setObserver.get();
    }

    /**
     * Trickle ICE
     *
     * @param payload
     */
    public void addIceCandidate(JSONObject payload) {
        RTCIceCandidate candidate = new RTCIceCandidate(payload.getString("sdpMid"),
                payload.getInt("sdpMLineIndex"),
                payload.getString("candidate"),
                payload.optString("serverUrl"));
        peerConnection.addIceCandidate(candidate);
    }


    public Channel createChannel(String name) {
        var dataChannel = this.peerConnection.createDataChannel(name, new RTCDataChannelInit());
        var channel = new Channel(dataChannel);
        this.channelMap.put(name, channel);
        return channel;
    }

    public void waitUntilConnected() throws InterruptedException {
        connectedLatch.await();
    }

    public void close() {
        logger.debug("Closing {}", this.configuration.getPairingCode());
        if (nonNull(this.defaultChannel)) {
            this.defaultChannel.close();
        }
        if (nonNull(this.peerConnection)) {
            this.peerConnection.close();
            this.peerConnection = null;
        }
    }

    @Override
    public void onSignalingChange(RTCSignalingState state) {

    }

    @Override
    public void onConnectionChange(RTCPeerConnectionState state) {
        this.fireConnectionStatusEvent(state);
    }

    @Override
    public void onIceConnectionChange(RTCIceConnectionState state) {
        logger.debug("onIceConnectionChange {}", state.toString());
    }

    @Override
    public void onStandardizedIceConnectionChange(RTCIceConnectionState state) {
        logger.debug("onStandardizedIceConnectionChange {}", state.toString());
    }

    @Override
    public void onIceConnectionReceivingChange(boolean receiving) {
        logger.debug("onIceConnectionReceivingChange {}", receiving);
    }

    @Override
    public void onIceGatheringChange(RTCIceGatheringState state) {
        logger.debug("onIceGatheringChange {}", state.toString());
        if (state == RTCIceGatheringState.COMPLETE) {
            this.onLastIceCandidate();
        }
    }

    @Override
    public void onIceCandidate(RTCIceCandidate candidate) {
        logger.debug("onIceCandidate {}", candidate.toString());
        this.onNewIceCandidate(candidate);
    }

    @Override
    public void onIceCandidateError(RTCPeerConnectionIceErrorEvent event) {
        logger.debug("onIceCandidateError {} {}", event.getAddress(), event.getErrorText());

    }

    @Override
    public void onIceCandidatesRemoved(RTCIceCandidate[] candidates) {
        logger.debug("onIceCandidatesRemoved");
    }

    @Override
    public void onAddStream(MediaStream stream) {

    }

    @Override
    public void onRemoveStream(MediaStream stream) {

    }

    @Override
    public void onDataChannel(RTCDataChannel dataChannel) {
        logger.debug("onDataChannel {}", dataChannel.getLabel());
        if (!this.defaultChannelName.equals(dataChannel.getLabel())) {
            var channel = new Channel(dataChannel);
            this.channelMap.put(dataChannel.getLabel(), channel);
            this.fireChannelEvent(channel);
        }
    }

    @Override
    public void onRenegotiationNeeded() {
    }

    @Override
    public void onAddTrack(RTCRtpReceiver receiver, MediaStream[] mediaStreams) {
    }

    @Override
    public void onRemoveTrack(RTCRtpReceiver receiver) {
    }

    @Override
    public void onTrack(RTCRtpTransceiver transceiver) {
    }

    public boolean addConnectionListener(ConnectionListener listener) {
        return this.connectionListeners.add(listener);
    }

    public boolean removeConnectionListener(ConnectionListener listener) {
        return this.connectionListeners.remove(listener);
    }

    private void fireConnectionStatusEvent(RTCPeerConnectionState state) {
        var mapState = this.mapState(state);
        if (nonNull(mapState)) {
            for (ConnectionListener listener : this.connectionListeners) {
                listener.onStateChange(mapState);
            }
        }
    }

    private ConnectionListener.State mapState(RTCPeerConnectionState state) {
        if (state == RTCPeerConnectionState.CONNECTED) {
            return ConnectionListener.State.OPEN;
        }
        if (state == RTCPeerConnectionState.DISCONNECTED) {
            return ConnectionListener.State.DISCONNECTED;
        }
        if (state == RTCPeerConnectionState.CLOSED) {
            return ConnectionListener.State.CLOSED;
        }
        return null;
    }

    private void fireChannelEvent(Channel channel) {
        for (ConnectionListener listener : this.connectionListeners) {
            listener.onChannel(channel);
        }
    }

    protected void onLastIceCandidate() {
    }

    protected void onNewIceCandidate(RTCIceCandidate candidate) {
    }

}
