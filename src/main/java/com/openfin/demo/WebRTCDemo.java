package com.openfin.demo;

import org.json.JSONObject;
import com.openfin.webrtc.CreateDescObserver;
import com.openfin.webrtc.SetDescObserver;
import dev.onvoid.webrtc.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;

import static java.util.Objects.nonNull;

public class WebRTCDemo implements PeerConnectionObserver {
    protected PeerConnectionFactory factory;
    private RTCPeerConnection localPeerConnection;
    private RTCPeerConnection remotePeerConnection;
    private RTCDataChannel localDataChannel;
    private RTCDataChannel remoteDataChannel;

    private CountDownLatch connectedLatch;

    public WebRTCDemo(PeerConnectionFactory factory) {
        this.factory = factory;
        RTCConfiguration config = new RTCConfiguration();
        localPeerConnection = factory.createPeerConnection(config, this);
        localDataChannel = localPeerConnection.createDataChannel("chat", new RTCDataChannelInit());
        localDataChannel.registerObserver(new RTCDataChannelObserver() {
            @Override
            public void onBufferedAmountChange(long previousAmount) { }

            @Override
            public void onStateChange() {
                System.out.println(String.format("onStateChange %s %s", localDataChannel.getLabel(), localDataChannel.getState()));
                if (localDataChannel.getState() == RTCDataChannelState.OPEN) {
                    try {
                        WebRTCDemo.this.sendTextMessage("Hello Java");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onMessage(RTCDataChannelBuffer buffer) {
                try {
                    decodeMessage(buffer);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        connectedLatch = new CountDownLatch(1);
    }

    public void setRemotePeerConnection(RTCPeerConnection remotePeerConnection) {
        this.remotePeerConnection = remotePeerConnection;
    }

    RTCSessionDescription createOffer() throws Exception {
        CreateDescObserver createObserver = new CreateDescObserver();
        SetDescObserver setObserver = new SetDescObserver();

        localPeerConnection.createOffer(new RTCOfferOptions(), createObserver);

        RTCSessionDescription offerDesc = createObserver.get();

        localPeerConnection.setLocalDescription(offerDesc, setObserver);
        setObserver.get();

        return offerDesc;
    }

    RTCSessionDescription createAnswer() throws Exception {
        CreateDescObserver createObserver = new CreateDescObserver();
        SetDescObserver setObserver = new SetDescObserver();

        localPeerConnection.createAnswer(new RTCAnswerOptions(), createObserver);

        RTCSessionDescription answerDesc = createObserver.get();

        localPeerConnection.setLocalDescription(answerDesc, setObserver);
        setObserver.get();

        return answerDesc;
    }

    void setRemoteDescription(RTCSessionDescription description) throws Exception {
        SetDescObserver setObserver = new SetDescObserver();
        localPeerConnection.setRemoteDescription(description, setObserver);
        setObserver.get();
    }

    @Override
    public void onConnectionChange(RTCPeerConnectionState state) {
        System.out.println(String.format("onConnectionChange %s", state));
        if (state == RTCPeerConnectionState.CONNECTED) {
            connectedLatch.countDown();
        }
    }

    void waitUntilConnected() throws InterruptedException {
        connectedLatch.await();
    }

    void sendTextMessage(String message) throws Exception {
        ByteBuffer data = ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));
        RTCDataChannelBuffer buffer = new RTCDataChannelBuffer(data, false);

        localDataChannel.send(buffer);
    }

    void close() {
        if (nonNull(localDataChannel)) {
            localDataChannel.unregisterObserver();
            localDataChannel.close();
            localDataChannel.dispose();
            localDataChannel = null;
        }
        if (nonNull(remoteDataChannel)) {
            remoteDataChannel.unregisterObserver();
            remoteDataChannel.close();
            remoteDataChannel.dispose();
            remoteDataChannel = null;
        }
        if (nonNull(localPeerConnection)) {
            localPeerConnection.close();
            localPeerConnection = null;
        }
    }

    @Override
    public void onIceCandidate(RTCIceCandidate candidate) {
        System.out.println(candidate.toString());
        if (nonNull(this.remotePeerConnection)) {
//            remotePeerConnection.addIceCandidate(candidate);
        }
    }

    @Override
    public void onIceGatheringChange(RTCIceGatheringState state) {
        System.out.println(String.format("onIceGatheringChange %s", state));
        if (state == RTCIceGatheringState.COMPLETE) {
            var offer = this.localPeerConnection.getLocalDescription();
            JSONObject offerPayload = new JSONObject();
            offerPayload.put("type", "offer");
            offerPayload.put("sdp", offer.sdp);
            System.out.println(offerPayload.toString());
        }
    }

    @Override
    public void onDataChannel(RTCDataChannel dataChannel) {
        System.out.println(String.format("onDataChannel ", dataChannel.getLabel()));
        remoteDataChannel = dataChannel;
        remoteDataChannel.registerObserver(new RTCDataChannelObserver() {

            @Override
            public void onBufferedAmountChange(long previousAmount) { }

            @Override
            public void onStateChange() { }

            @Override
            public void onMessage(RTCDataChannelBuffer buffer) {
                try {
                    decodeMessage(buffer);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void decodeMessage(RTCDataChannelBuffer buffer) {
        ByteBuffer byteBuffer = buffer.data;
        byte[] payload;

        if (byteBuffer.hasArray()) {
            payload = byteBuffer.array();
        }
        else {
            payload = new byte[byteBuffer.limit()];

            byteBuffer.get(payload);
        }

        String text = new String(payload, StandardCharsets.UTF_8);
        System.out.println(text);
    }

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        var factory = new PeerConnectionFactory();
        WebRTCDemo caller1 = new WebRTCDemo(factory);

        var offer = caller1.createOffer();
        JSONObject offerPayload = new JSONObject();
        offerPayload.put("type", "offer");
        offerPayload.put("sdp", offer.sdp);
        System.out.println(offerPayload.toString());

//        JSONObject candidatePayload = (JSONObject) jsonParser.parse(sc.nextLine());
//        caller1.localPeerConnection.addIceCandidate(new RTCIceCandidate(
//                (String)candidatePayload.get("sdpMid"),
//                ((Long) candidatePayload.get("sdpMLineIndex")).intValue(), (String) candidatePayload.get("candidate")));

        JSONObject answerPayload = new JSONObject(sc.nextLine());
        String sdp = (String) answerPayload.get("sdp");
        caller1.setRemoteDescription(new RTCSessionDescription(RTCSdpType.ANSWER, sdp));

        caller1.waitUntilConnected();

        Thread.sleep(50000);

        caller1.close();
    }

    public static void main2(String[] args) throws Exception {
        var factory = new PeerConnectionFactory();
        WebRTCDemo caller1 = new WebRTCDemo(factory);
        WebRTCDemo caller2 = new WebRTCDemo(factory);

        caller1.setRemotePeerConnection(caller2.localPeerConnection);
        caller2.setRemotePeerConnection(caller1.localPeerConnection);

        caller1.setRemoteDescription(caller2.createOffer());
        caller2.setRemoteDescription(caller1.createAnswer());

        caller1.waitUntilConnected();
        caller2.waitUntilConnected();

        caller1.sendTextMessage("Hello world");
        caller2.sendTextMessage("Hi :)");

        Thread.sleep(50000);

        caller1.close();
        caller2.close();
    }

}
