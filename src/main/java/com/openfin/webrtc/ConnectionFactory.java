/**
 * Factory for creating com.openfin.webrtc.Connection
 */
package com.openfin.webrtc;

import static java.util.Objects.nonNull;

public class ConnectionFactory {

    public ConnectionFactory() {
    }

    /**
     * Create an instance of com.openfin.webrtc.Connection with the configuration.  If DesktopConnection is set in the configuration,
     * an instance of com.openfin.webrtc.LocalConnection is created.
     *
     * @param configuration connection Configuration
     * @return com.openfin.webrtc.Connection
     * @throws Exception
     */
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
