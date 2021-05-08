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
import java.util.concurrent.CountDownLatch;

public class WebRTCPerf implements DesktopStateListener, ConnectionListener {
    private final static Logger logger = LoggerFactory.getLogger(WebRTCPerf.class);

    private final DesktopConnection desktopConnection;
    private static final CountDownLatch latch = new CountDownLatch(1);
    private Connection webRTCConnection;
    private Channel channel;
    private JFrame demoWindow;
    private JPanel glassPane;

    private Timer timer;
    private JTextField tfMSize;
    private JTextField tfMPS;
    private JTextField tfTotalCount;
    // performance Metrics
    private volatile long currentMessageId = 0;
    private volatile int lastMPS = 0;
    private volatile  long currentStartTime = 0;
    private volatile int currentCount = 0;

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
        desktopConnection.connect(configuration, this, 60);

        this.timer = new Timer(1000, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                WebRTCPerf.this.updateMetrics();
            }
        });
        this.timer.setInitialDelay(0);
        this.timer.start();
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
        tfTotalCount.setEnabled(false);
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


        JButton btnStart = new JButton("Start");

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
    @Override
    public void onReady() {
        logger.info("onReady");
        Configuration cfg = new Configuration();
        cfg.setPairingCode("fastBus");
        try {
            this.webRTCConnection = new Connection(cfg, this.desktopConnection);
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
        this.channel = channel;
        this.channel.addChannelListener(new ChannelListener() {
            @Override
            public void onStateChange(State state) {
                logger.info("New Channel State {}", state.toString());
            }
            @Override
            public void onMessage(String message) {
                WebRTCPerf.this.onPerfData(message);
            }
        });
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
    }
}
