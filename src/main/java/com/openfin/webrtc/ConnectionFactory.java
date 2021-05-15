package com.openfin.webrtc;

import static java.util.Objects.nonNull;
import static java.util.Objects.isNull;

public class ConnectionFactory {

    public ConnectionFactory() {
    }

    public Connection createConnection(Configuration configuration) throws Exception {
        Connection connection;
        if (nonNull(configuration.getDesktopConnection())) {
            connection = new LocalConnection(configuration);
        } else {
            connection = new IceConnection(configuration);
        }
        connection.initialize();
        return connection;
    }
}
