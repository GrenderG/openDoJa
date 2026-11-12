package com.docomostar.io;

import java.io.IOException;
import javax.microedition.io.StreamConnection;

/** STAR peer-socket contract. Transport availability is determined by Connector. */
public interface SocketConnection extends StreamConnection {
    void close() throws IOException;
    void connect() throws IOException;
    void connect(String address, int port) throws IOException;
    void listen(String address, int port) throws IOException;
    void openRemotePort() throws IOException;
    String getAddress();
    int getPort();
    String getLocalAddress();
    int getLocalPort();
}
