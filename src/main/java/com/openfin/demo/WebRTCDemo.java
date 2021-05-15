package com.openfin.demo;

import com.openfin.desktop.DesktopConnection;
import com.openfin.desktop.DesktopStateListener;
import com.openfin.desktop.RuntimeConfiguration;
import com.openfin.webrtc.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.CountDownLatch;

public class WebRTCDemo implements DesktopStateListener, ConnectionListener {
    private final static Logger logger = LoggerFactory.getLogger(WebRTCDemo.class);

    private final DesktopConnection desktopConnection;
    private static final CountDownLatch latch = new CountDownLatch(1);
    private Connection webRTCConnection;
    private Channel channel;
    private final ChannelListener channelListener;

    private JFrame demoWindow;
    private JPanel glassPane;

    private JTextField tfChannelName;
    private JTextField tfOutgoingText;
    private JTextField tfIncomingText;
    private JButton btnSend;

    public WebRTCDemo() throws Exception {
        this.demoWindow = new JFrame("OpenFin WebRTC Demo");
        this.demoWindow.setContentPane(this.createContentPanel());
        this.demoWindow.setGlassPane(this.createGlassPane());
        this.demoWindow.pack();
        this.demoWindow.setLocationRelativeTo(null);
        this.demoWindow.setVisible(true);
        this.glassPane.setVisible(true);

        desktopConnection = new DesktopConnection(WebRTCDemo.class.getName());
        String desktopVersion = java.lang.System.getProperty("com.openfin.demo.runtime.version", "stable");
        RuntimeConfiguration configuration = new RuntimeConfiguration();
        configuration.setRuntimeVersion(desktopVersion);
        this.channelListener = new ChannelListener() {
            @Override
            public void onStateChange(State state) {
                logger.info("New Channel State {}", state.toString());
            }
            @Override
            public void onMessage(String message) {
                WebRTCDemo.this.tfIncomingText.setText(message);
            }
        };

        desktopConnection.connect(configuration, this, 60);
    }

    private JPanel createGlassPane() {
        this.glassPane = new JPanel(new BorderLayout());
        JLabel l = new JLabel("Loading, please wait......");
        l.setHorizontalAlignment(JLabel.CENTER);
        this.glassPane.add(l, BorderLayout.CENTER);
        this.glassPane.setBackground(Color.LIGHT_GRAY);
        return this.glassPane;
    }

    private JPanel createContentPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        p.setPreferredSize(new Dimension(550, 200));
        p.add(this.createPerfPanel(), BorderLayout.CENTER);
        return p;
    }

    private JPanel createPerfPanel() {
        JPanel pnl = new JPanel(new BorderLayout());
        pnl.setBorder(BorderFactory.createTitledBorder("WebRTC Channel"));
        this.tfChannelName = new JTextField("JavaWebRTC");
        this.tfOutgoingText = new JTextField("");
        this.tfIncomingText = new JTextField("");
        JPanel pnlCenter = new JPanel(new GridBagLayout());
        GridBagConstraints gbConst = new GridBagConstraints();
        gbConst.gridx = 0;
        gbConst.gridy = 0;
        gbConst.weightx = 0;
        gbConst.insets = new Insets(5, 5, 5, 5);
        gbConst.anchor = GridBagConstraints.EAST;
        pnlCenter.add(new JLabel("Channel Name"), gbConst);
        gbConst.gridy++;
        pnlCenter.add(new JLabel("Outgoing Text"), gbConst);
        gbConst.gridy++;
        pnlCenter.add(new JLabel("Incoming Text"), gbConst);
        gbConst.gridy++;
        gbConst.gridx = 1;
        gbConst.gridy = 0;
        gbConst.weightx = 0.5;
        gbConst.insets = new Insets(5, 0, 5, 5);
        gbConst.fill = GridBagConstraints.BOTH;
        pnlCenter.add(tfChannelName, gbConst);
        gbConst.gridy++;
        pnlCenter.add(tfOutgoingText, gbConst);
        gbConst.gridy++;
        pnlCenter.add(tfIncomingText, gbConst);
        gbConst.gridy++;
        pnlCenter.add(new JLabel(), gbConst);

        this.btnSend = new JButton("Send");
        this.btnSend.setEnabled(false);
        this.btnSend.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                WebRTCDemo.this.sendChat();
            }
        });

        JPanel pnlBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        pnlBottom.add(btnSend);
        pnl.add(pnlCenter, BorderLayout.CENTER);
        pnl.add(pnlBottom, BorderLayout.SOUTH);
        return pnl;
    }

    private void sendChat() {
        try {
            if (this.channel == null) {
                this.channel = this.webRTCConnection.createChannel(this.tfChannelName.getText());
                this.channel.addChannelListener(this.channelListener);
            }
            JSONObject chat = new JSONObject();
            chat.put("type", "chat");
            chat.put("text", this.tfOutgoingText.getText());
            this.channel.send(chat.toString());
        } catch (Exception ex) {
            logger.error("Error sending text", ex);
        }
    }

    @Override
    public void onReady() {
        logger.info("onReady");
        Configuration cfg = new Configuration();
        cfg.setPairingCode("JavaWebRTCDemo");
        cfg.setSignalingBaseUrl("https://webrtc-signaling-dev.openfin.co");
        var discovery = System.getProperty("com.openfin.demo.webrtc.discovery");
        if (!"signaling".equals(discovery)) {
            cfg.setDesktopConnection(this.desktopConnection);
        }
        try {
            ConnectionFactory factory = new ConnectionFactory();
            this.webRTCConnection = factory.createConnection(cfg);
            this.webRTCConnection.addConnectionListener(this);

            SwingUtilities.invokeLater(()->{
                glassPane.setVisible(false);
            });
        } catch (Exception ex) {
            logger.error("Error creating connection", ex);
        }
    }

    @Override
    public void onClose(String s) {
        logger.info("onClose, value={}", s);
        latch.countDown();
    }

    @Override
    public void onError(String error) {
        logger.info("onError, value={}", error);
        latch.countDown();
    }

    @Override
    public void onMessage(String s) {
        logger.info("onMessage {}", s);
    }

    @Override
    public void onOutgoingMessage(String s) {
    }

    public static void main(String[] args) throws Exception {
        new WebRTCDemo();
        latch.await();
    }

    @Override
    public void onStateChange(ConnectionListener.State state) {
        logger.info("new Connection state {}", state.toString());
        if (state == State.OPEN) {
            this.btnSend.setEnabled(true);
        } else {
            this.btnSend.setEnabled(false);
        }
    }

    @Override
    public void onChannel(Channel channel) {
        logger.info("new Channel {}", channel.getName());
        this.channel = channel;
        this.tfChannelName.setText(channel.getName());
        this.channel.addChannelListener(this.channelListener);
    }

}
