package com.openfin.webrtc;

import com.openfin.desktop.AsyncCallback;
import com.openfin.desktop.DesktopConnection;
import com.openfin.desktop.channel.ChannelAction;
import com.openfin.desktop.channel.ChannelClient;
import dev.onvoid.webrtc.*;
import dev.onvoid.webrtc.media.MediaStream;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import static java.util.Objects.nonNull;

public class Connection implements PeerConnectionObserver {
    private final static Logger logger = LoggerFactory.getLogger(Connection.class);
    private final CountDownLatch connectedLatch;

    private final Configuration configuration;
    private final PeerConnectionFactory factory;
    private RTCPeerConnection peerConnection;
    private RTCDataChannel defaultDataChannel;
    private String defaultChannelName;  // name of default DataChannel
    private Map<String, Channel> channelMap;

    private final DesktopConnection desktopConnection;
    private final String ofChannelName;
    private ChannelClient ofChannelClient;
    private static final String OFFER_ACTION  = "offer-description";
    private static final String ANSWER_ACTION = "answer-description";
    private CopyOnWriteArrayList<ConnectionListener> connectionListeners;

    public Connection(Configuration configuration, DesktopConnection desktopConnection) {
        this.configuration = configuration;
        this.desktopConnection = desktopConnection;
        this.defaultChannelName = String.format("%s:default", configuration.getPairingCode());
        this.ofChannelName = String.format("webrtc:%s:offer:answer", this.configuration.getPairingCode());
        this.factory = new PeerConnectionFactory();
        RTCConfiguration config = new RTCConfiguration();
        this.peerConnection = factory.createPeerConnection(config, this);
        this.connectedLatch = new CountDownLatch(1);
        this.channelMap = new ConcurrentHashMap<>();
        this.connectionListeners = new CopyOnWriteArrayList<>();
        logger.debug("Created Connection with default channel {} OF channel {}", this.defaultChannelName, this.ofChannelName);
    }

    // Only support OFFER for now
    public void initializeOffer() throws Exception {
        this.defaultDataChannel = this.peerConnection.createDataChannel(this.defaultChannelName, new RTCDataChannelInit());
        this.createOffer();
    }

    private void createOffer() throws Exception {
        CreateDescObserver createObserver = new CreateDescObserver();
        SetDescObserver setObserver = new SetDescObserver();
        peerConnection.createOffer(new RTCOfferOptions(), createObserver);
        RTCSessionDescription offerDesc = createObserver.get();
        peerConnection.setLocalDescription(offerDesc, setObserver);
        setObserver.get();
    }

    private void makeOffer() {
        var offer = this.peerConnection.getLocalDescription();
        JSONObject description = new JSONObject();
        description.put("type", "offer");
        description.put("sdp", offer.sdp);
        JSONObject payload = new JSONObject();
        payload.put("description", description);
        logger.debug("last ICE candidate {}", payload.toString());
        this.ofChannelClient.dispatch(OFFER_ACTION, payload, null);
    }

    private RTCSessionDescription createAnswer() throws Exception {
        CreateDescObserver createObserver = new CreateDescObserver();
        SetDescObserver setObserver = new SetDescObserver();
        peerConnection.createAnswer(new RTCAnswerOptions(), createObserver);
        RTCSessionDescription answerDesc = createObserver.get();
        peerConnection.setLocalDescription(answerDesc, setObserver);
        setObserver.get();
        return answerDesc;
    }

    private JSONObject onAnswer(JSONObject payload) {
        logger.debug("Got answer {}", payload.toString());
        JSONObject ret = new JSONObject();
        try {
            JSONObject description = payload.getJSONObject("description");
            this.setRemoteDescription(new RTCSessionDescription(RTCSdpType.ANSWER, description.getString("sdp")));
            ret.put("status", 200);
        } catch (Exception ex) {
            logger.error("Error setRemoteDescription", ex);
            ret.put("status", 500);
            ret.put("message", ex.getMessage());
        }
        return ret;
    }

    private void setRemoteDescription(RTCSessionDescription description) throws Exception {
        SetDescObserver setObserver = new SetDescObserver();
        peerConnection.setRemoteDescription(description, setObserver);
        setObserver.get();
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
        if (nonNull(this.defaultDataChannel)) {
            this.defaultDataChannel.unregisterObserver();
            this.defaultDataChannel.close();
            this.defaultDataChannel.dispose();
            this.defaultDataChannel = null;
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
            this.createOFChannelClient();
        }
    }

    @Override
    public void onIceCandidate(RTCIceCandidate candidate) {
        logger.debug("onIceCandidate {}", candidate.toString());
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
        var channel = new Channel(dataChannel);
        this.channelMap.put(dataChannel.getLabel(), channel);
        this.fireChannelEvent(channel);
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

    private void createOFChannelClient() {
        logger.debug("Connecting to OpenFin Channel {}", this.ofChannelName);
        this.desktopConnection.getChannel(this.ofChannelName).connect(new AsyncCallback<ChannelClient>() {
            @Override
            public void onSuccess(ChannelClient client) {
                logger.debug("Connected to OpenFin Channel {}", client.getName());
                Connection.this.ofChannelClient = client;
                client.register(ANSWER_ACTION, new ChannelAction() {
                    @Override
                    public JSONObject invoke(String s, JSONObject payload, JSONObject senderIdentity) {
                        return Connection.this.onAnswer(payload);
                    }
                });
                try {
                    Connection.this.makeOffer();
                } catch (Exception ex) {
                    logger.error("Error creating offer", ex);
                }
            }
        });
    }
}
