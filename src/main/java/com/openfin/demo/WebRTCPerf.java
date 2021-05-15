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

public class WebRTCPerf implements DesktopStateListener, ConnectionListener {
    private final static Logger logger = LoggerFactory.getLogger(WebRTCPerf.class);

    private final DesktopConnection desktopConnection;
    private static final CountDownLatch latch = new CountDownLatch(1);
    private Connection webRTCConnection;
    private Channel channel;
    private ChannelListener channelListener;
    private static String PERFORMANCE_CHANNEL_NAME = "WebRTCPerfChannel";
    private JSONObject sampleMessage;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");

    private JFrame demoWindow;
    private JPanel glassPane;

    private Timer statTimer, sendTimer;
    private JTextField tfMSize;
    private JTextField tfMPS;
    private JTextField tfTotalCount;
    private JButton btnStart;

    // performance Metrics
    private long currentMessageId = 0;
    private int lastMPS = 0;
    private long currentStartTime = 0;
    private int currentCount = 0;

    public WebRTCPerf() throws Exception {
        this.demoWindow = new JFrame("OpenFin WebRTC Performance test");
        this.demoWindow.setContentPane(this.createContentPanel());
        this.demoWindow.setGlassPane(this.createGlassPane());
        this.demoWindow.pack();
        this.demoWindow.setLocationRelativeTo(null);
        this.demoWindow.setVisible(true);
        this.glassPane.setVisible(true);

        desktopConnection = new DesktopConnection(WebRTCPerf.class.getName());
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
                WebRTCPerf.this.onPerfData(message);
            }
        };

        this.statTimer = new Timer(1000, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                WebRTCPerf.this.updateMetrics();
            }
        });
        this.statTimer.setInitialDelay(0);

        this.sendTimer = new Timer(1000, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                WebRTCPerf.this.spam();
            }
        });
        this.sendTimer.setInitialDelay(0);

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
        this.tfMSize = new JTextField("1024");
        this.tfMPS = new JTextField("500");
        this.tfTotalCount = new JTextField("");
        JPanel pnlCenter = new JPanel(new GridBagLayout());
        GridBagConstraints gbConst = new GridBagConstraints();
        gbConst.gridx = 0;
        gbConst.gridy = 0;
        gbConst.weightx = 0;
        gbConst.insets = new Insets(5, 5, 5, 5);
        gbConst.anchor = GridBagConstraints.EAST;
        pnlCenter.add(new JLabel("Message Size"), gbConst);
        gbConst.gridy++;
        pnlCenter.add(new JLabel("MPS"), gbConst);
        gbConst.gridy++;
        pnlCenter.add(new JLabel("Total Count"), gbConst);
        gbConst.gridy++;
        gbConst.gridx = 1;
        gbConst.gridy = 0;
        gbConst.weightx = 0.5;
        gbConst.insets = new Insets(5, 0, 5, 5);
        gbConst.fill = GridBagConstraints.BOTH;
        pnlCenter.add(tfMSize, gbConst);
        gbConst.gridy++;
        pnlCenter.add(tfMPS, gbConst);
        gbConst.gridy++;
        pnlCenter.add(tfTotalCount, gbConst);
        gbConst.gridy++;
        pnlCenter.add(new JLabel(), gbConst);

        this.btnStart = new JButton("Start");
        this.btnStart.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                WebRTCPerf.this.toggleSend();
            }
        });

        JPanel pnlBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        pnlBottom.add(btnStart);
        pnl.add(pnlCenter, BorderLayout.CENTER);
        pnl.add(pnlBottom, BorderLayout.SOUTH);
        return pnl;
    }

    private void updateMetrics() {
        if (this.currentMessageId > 0) {
            this.tfMPS.setText(String.format("%d", this.lastMPS));
            this.tfTotalCount.setText(String.format("%d", this.currentMessageId));
        }
    }

    private void toggleSend() {
        if (this.sendTimer.isRunning()) {
            this.sendTimer.stop();
            this.btnStart.setText("Start");
//            this.channel.removeChannelListener(this.channelListener);
//            this.channel.close();
//            this.channel = null;
        } else {
            this.btnStart.setText("Stop");
            if (this.channel == null) {
                this.channel = this.webRTCConnection.createChannel(PERFORMANCE_CHANNEL_NAME);
                this.channel.addChannelListener(this.channelListener);
            }
            this.currentMessageId = 0;
            this.currentStartTime = 0;
            this.currentCount = 0;
            this.sampleMessage = new JSONObject();
            int len = Integer.parseInt(this.tfMSize.getText());
            String payload = String.format("%0" + len + "d", 8);
            this.sampleMessage.put("payload", payload);
            this.sendTimer.restart();
            this.statTimer.stop();
            logger.info("Starting with MSP {} msg size {}", Integer.parseInt(this.tfMPS.getText()), payload.length());
        }
    }

    private void spam() {
        long start = System.currentTimeMillis();
        for (int i = 0; i < Integer.parseInt(this.tfMPS.getText()); i++) {
            this.currentMessageId += 1;
            this.sampleMessage.put("id", this.currentMessageId);
            try {
                this.channel.send(this.sampleMessage.toString());
            } catch (Exception ex) {
                logger.error("Error sending", ex);
                this.toggleSend();
            }
//            if (dataChannelRef.current.bufferedAmount >= threshold) {
//                setInfoText(`throttling ${dataChannelRef.current.bufferedAmount}`);
//                console.log(`throttling ${dataChannelRef.current.bufferedAmount}`);
//                toggleSend();
//                break;
//            }
        }
        this.tfTotalCount.setText(String.format("%d", this.currentMessageId));
        logger.info("{} {} {}", dateFormat.format(new Date()), this.currentMessageId, (System.currentTimeMillis() - start) );
        int elapse = (int) (System.currentTimeMillis() - start - 1000);
        sendTimer.setDelay( elapse > 0 ? elapse : 0  );
    }

    @Override
    public void onReady() {
        logger.info("onReady");
        Configuration cfg = new Configuration();
        cfg.setPairingCode("fastBus");
        cfg.setDesktopConnection(this.desktopConnection);
        try {
            ConnectionFactory factory = new ConnectionFactory();
            this.webRTCConnection = factory.createConnection(cfg);
            this.webRTCConnection.addConnectionListener(this);

            this.webRTCConnection.initializeOffer();

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
        new WebRTCPerf();
        latch.await();
    }

    @Override
    public void onStateChange(ConnectionListener.State state) {
        logger.info("new Connection state {}", state.toString());
    }

    @Override
    public void onChannel(Channel channel) {
        logger.info("new Channel {}", channel.getName());
        if (this.channel.getName().equals(PERFORMANCE_CHANNEL_NAME)) {
            this.channel = channel;
            this.channel.addChannelListener(this.channelListener);
        }
    }

    private void onPerfData(String s) {
        JSONObject msg = new JSONObject(s);
        this.currentMessageId = msg.getInt("id");
        if (this.currentMessageId == 1) {
            this.currentStartTime = 0;
            this.currentCount = 0;
            this.lastMPS = 0;
        }
        this.currentCount++;
        long now = System.currentTimeMillis();
        if (this.currentStartTime == 0) {
            this.currentStartTime = now;
        }
        else if ((now - this.currentStartTime) >= 1000) {
            this.lastMPS = this.currentCount;
            this.currentCount = 0;
            this.currentStartTime = now;
        }
        if (!this.statTimer.isRunning()) {
            this.statTimer.start();
        }
    }
}
