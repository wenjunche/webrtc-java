package com.openfin.webrtc;

import dev.onvoid.webrtc.RTCIceCandidate;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import io.socket.engineio.client.transports.WebSocket;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CountDownLatch;
import static java.util.Objects.isNull;

public class Signaling {
    private final static Logger logger = LoggerFactory.getLogger(Signaling.class);
    private static final CountDownLatch latch = new CountDownLatch(1);

    private final Configuration configuration;
    private HttpClient httpClient;
    private Socket socket;
    private SignalingListener signalingListener;
    private boolean peerLeader;

    public Signaling(Configuration configuration) {
        this.configuration = configuration;
        this.peerLeader = false;
    }

    public void setSignalingListener(SignalingListener listener) {
        this.signalingListener = listener;
    }

    public void initialize() throws Exception {
        CookieHandler.setDefault(new CookieManager());
        this.httpClient = HttpClient.newBuilder()
                .cookieHandler(CookieHandler.getDefault())
                .build();
        URI uri = URI.create(String.format("%s/api/auth/check", this.configuration.getSignalingBaseUrl()));
        var response = this.makeHTTPRequest(uri);
        var cookies = this.httpClient.cookieHandler().get().get(uri, response.headers().map());

        IO.Options options = IO.Options.builder()
                .setMultiplex(true)
                .setUpgrade(true)
                .setTransports(new String[]{WebSocket.NAME})
                .setExtraHeaders(cookies)
                .setTimeout(10000)
                .build();
        this.socket = IO.socket(URI.create(this.configuration.getSignalingBaseUrl()), options);
        this.addSocketListeners();
        logger.debug("Connecting {}", this.configuration.getSignalingBaseUrl());
        this.socket.connect();
    }

    private HttpResponse<String> makeHTTPRequest(URI uri) throws Exception {
        var request = HttpRequest.newBuilder().uri(uri)
                .version(HttpClient.Version.HTTP_1_1)
                .GET()
                .build();
        logger.debug("Requesting {}", request.uri().toString());
        var response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != HttpURLConnection.HTTP_OK) {
            throw new Exception(String.format("Signaling auth check failed %d", response.statusCode()));
        }
        return response;
    }

    private void addSocketListeners() {
        this.socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... objects) {
                logger.info("Connected {}", configuration.getSignalingBaseUrl());
                Signaling.this.socket.emit("join", configuration.getPairingCode());
            }
        });
        this.socket.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... objects) {
                logger.info("Disconnected {}", configuration.getSignalingBaseUrl());
            }
        });
        this.socket.on(Socket.EVENT_CONNECT_ERROR, new Emitter.Listener() {
            @Override
            public void call(Object... objects) {
                logger.info("Connect Error {}", configuration.getSignalingBaseUrl());
            }
        });

        this.socket.on("ready", new Emitter.Listener() {
            @Override
            public void call(Object... objects) {
                logger.info("Room is ready {} leader {}", objects[0], objects[1]);
                Signaling.this.peerLeader = Signaling.this.socket.id().equals(objects[1]);
                logger.debug("setting peer leader {}", Signaling.this.peerLeader);
                Signaling.this.createWebRTCConnection();
            }
        });

        this.socket.on("joined", new Emitter.Listener() {
            @Override
            public void call(Object... objects) {
                logger.info("This peer has joined room {} with client ID {}", objects[0], objects[1]);
            }
        });

        this.socket.on("message", new Emitter.Listener() {
            @Override
            public void call(Object... objects) {
                logger.info("Got message {} ", objects[0]);
                Signaling.this.processSignalingMessage((JSONObject) objects[0]);
            }
        });

        this.socket.on("disconnect", new Emitter.Listener() {
            @Override
            public void call(Object... objects) {
                logger.info("Disconnected {}", objects[0]);
            }
        });
    }

    private void createWebRTCConnection() {
        try {
            URI uri = URI.create(String.format("%s/api/webrtc/rtcConfig", this.configuration.getSignalingBaseUrl()));
            var response = this.makeHTTPRequest(uri);
            logger.debug("Got {}", response.body());
            JSONObject config = new JSONObject(response.body());
            this.signalingListener.onRtcConfig(config.getJSONObject("rtcConfig"));
        } catch (Exception ex) {
            logger.error("Error creating WebRTC connection", ex);
        }
    }

    private void processSignalingMessage(JSONObject message) {
        logger.debug("Got signaling message {}", message);
        String type = message.getString("type");
        if (Connection.SDPOffer.equals(type)) {
            logger.debug("got offer");
            this.signalingListener.onSignalingOffer(message);
        }
        else if (Connection.SDPAnswer.equals(type)) {
            this.signalingListener.onSignalingAnswer(message);
        }
        else if (Connection.SDPCandidate.equals(type)) {
            this.signalingListener.onSignalingIceCandidate(message.getJSONObject("candidate"));
        }
    }

    public Emitter emit(final String event, final Object... args) throws Exception {
        if (this.socket.connected()) {
            return this.socket.emit(event, args);
        } else {
            throw new Exception("Socket is not connected");
        }
    }

    public boolean isPeerLeader() {
        return this.peerLeader;
    }

    public static void main(String[] args) throws Exception {
        Configuration cfg = new Configuration();
        cfg.setPairingCode("JavaSignaling");
        cfg.setSignalingBaseUrl("https://webrtc-signaling-dev.openfin.co");
        ConnectionFactory factory = new ConnectionFactory();
        factory.createConnection(cfg);
        latch.await();
    }
}
