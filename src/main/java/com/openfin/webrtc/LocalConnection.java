package com.openfin.webrtc;

import com.openfin.desktop.AsyncCallback;
import com.openfin.desktop.DesktopConnection;
import com.openfin.desktop.channel.ChannelAction;
import com.openfin.desktop.channel.ChannelClient;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CopyOnWriteArrayList;

public class LocalConnection extends Connection {
    private final static Logger logger = LoggerFactory.getLogger(LocalConnection.class);

    private final DesktopConnection desktopConnection;
    private final String ofChannelName;
    private ChannelClient ofChannelClient;
    private static final String OFFER_ACTION  = "offer-description";
    private static final String ANSWER_ACTION = "answer-description";
    private CopyOnWriteArrayList<ConnectionListener> connectionListeners;

    public LocalConnection(Configuration configuration) {
        super(configuration);

        this.desktopConnection = configuration.getDesktopConnection();
        this.ofChannelName = String.format("webrtc:%s:offer:answer", this.configuration.getPairingCode());

        logger.debug("Created Connection with OF channel {}", this.ofChannelName);
    }

    @Override
    public void initialize() throws Exception {
        this.createPeerConnection(null);
    }

    @Override
    protected JSONObject makeOffer() throws Exception {
        var offer = super.makeOffer();
        this.ofChannelClient.dispatch(OFFER_ACTION, offer, null);
        return offer;
    }

    @Override
    protected void onLastIceCandidate() {
        super.onLastIceCandidate();
        logger.debug("Connecting to OpenFin Channel {}", this.ofChannelName);
        this.desktopConnection.getChannel(this.ofChannelName).connect(new AsyncCallback<ChannelClient>() {
            @Override
            public void onSuccess(ChannelClient client) {
                logger.debug("Connected to OpenFin Channel {}", client.getName());
                LocalConnection.this.ofChannelClient = client;
                client.register(ANSWER_ACTION, new ChannelAction() {
                    @Override
                    public JSONObject invoke(String s, JSONObject payload, JSONObject senderIdentity) {
                        return LocalConnection.this.onAnswer(payload.getJSONObject("description"));
                    }
                });
                try {
                    LocalConnection.this.makeOffer();
                } catch (Exception ex) {
                    logger.error("Error creating offer", ex);
                }
            }
        });
    }

}
