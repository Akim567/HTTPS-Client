package netdiag;

import netdiag.connect.TcpConnector;
import netdiag.dns.DnsResolver;
import netdiag.http.HttpRequestBuilder;
import netdiag.url.RequestTarget;
import netdiag.url.UrlParser;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public class Main {
    private static final int TIMEOUT_MILLIS = 5_000;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: netdiag <url>");
            return;
        }

        String host = null;
        int port = -1;

        try {
            RequestTarget target = UrlParser.create(args[0]);
            host = target.getHost();
            port = target.getPort();

            InetAddress[] addresses = DnsResolver.resolve(host);

            System.out.println("Resolved IP addresses:");
            for (InetAddress address : addresses) {
                System.out.println(address.getHostAddress());
            }

            try (Socket socket = TcpConnector.connect(
                    addresses[0],
                    port,
                    TIMEOUT_MILLIS
            )) {
                byte[] requestBytes =
                        HttpRequestBuilder.buildGetRequest(target);

                OutputStream output = socket.getOutputStream();
                output.write(requestBytes);
                output.flush();

                System.out.println("HTTP request sent");
            }
        } catch (UnknownHostException exception) {
            System.out.println("STAGE: DNS");
            System.out.println("RESULT: error");
            System.out.println("CLASS: dns_error");
            System.out.printf("FACT: host %s could not be resolved%n", host);
            System.out.println("NEXT: check the hostname and DNS settings");
        } catch (SocketTimeoutException exception) {
            System.out.println("STAGE: CONNECT");
            System.out.println("RESULT: error");
            System.out.println("CLASS: timeout");
            System.out.printf("FACT: connection to %s:%d timed out%n", host, port);
            System.out.println("NEXT: check that the server is reachable");
        } catch (ConnectException | NoRouteToHostException exception) {
            System.out.println("STAGE: CONNECT");
            System.out.println("RESULT: error");
            System.out.println("CLASS: connect_error");
            System.out.printf("FACT: connection to %s:%d failed: %s%n",
                    host, port, exception.getMessage());
            System.out.println("NEXT: check that the server is running and the port is correct");
        } catch (IOException exception) {
            System.out.println("STAGE: CONNECT");
            System.out.println("RESULT: error");
            System.out.println("CLASS: connect_error");
            System.out.printf("FACT: connection to %s:%d failed: %s%n",
                    host, port, exception.getMessage());
            System.out.println("NEXT: inspect the network connection");
        } catch (IllegalArgumentException exception) {
            System.err.printf("Invalid URL: %s%n", exception.getMessage());
        }
    }
}
