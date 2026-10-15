package netdiag.tls;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.Socket;

/**
 * Запускает TLS поверх уже установленного TCP-соединения.
 */
public final class TlsConnector {
    private TlsConnector() {
    }

    public static SSLSocket connect(
            Socket tcpSocket,
            String host,
            int port,
            int timeoutMillis,
            SSLContext sslContext
    ) throws IOException {
        if (tcpSocket == null || !tcpSocket.isConnected() || tcpSocket.isClosed()) {
            throw new IllegalArgumentException("TCP socket must be connected and open");
        }
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("TLS host must not be empty");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("TLS port must be between 1 and 65535");
        }
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("TLS timeout must be greater than zero");
        }
        if (sslContext == null) {
            throw new IllegalArgumentException("SSL context must not be null");
        }

        SSLSocket tlsSocket = (SSLSocket) sslContext.getSocketFactory().createSocket(
                tcpSocket,
                host,
                port,
                true
        );

        try {
            tlsSocket.setUseClientMode(true);
            tlsSocket.setSoTimeout(timeoutMillis);

            SSLParameters parameters = tlsSocket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm(null);
            tlsSocket.setSSLParameters(parameters);

            tlsSocket.startHandshake();
            return tlsSocket;
        } catch (IOException exception) {
            try {
                tlsSocket.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }
}
