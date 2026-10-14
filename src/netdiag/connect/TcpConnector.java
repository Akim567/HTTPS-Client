package netdiag.connect;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Устанавливает TCP-соединение с разрешённым IP-адресом и портом.
 */
public final class TcpConnector {
    private TcpConnector() {
    }

    public static Socket connect(InetAddress address, int port, int timeoutMillis)
            throws IOException {
        if (address == null) {
            throw new IllegalArgumentException("IP address must not be null");
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }

        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("Timeout must be greater than zero");
        }

        Socket socket = new Socket();
        try {
            InetSocketAddress socketAddress = new InetSocketAddress(address, port);
            socket.connect(socketAddress, timeoutMillis);
            socket.setSoTimeout(timeoutMillis);
            return socket;
        } catch (IOException exception) {
            try {
                socket.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }

            throw exception;
        }
    }
}
