package netdiag;

import netdiag.connect.TcpConnector;
import netdiag.decode.CharsetResolver;
import netdiag.decode.ContentEncodingDecoder;
import netdiag.decode.TextDecoder;
import netdiag.diagnostic.DiagnosticPrinter;
import netdiag.diagnostic.DiagnosticResult;
import netdiag.diagnostic.JsonStringEncoder;
import netdiag.dns.DnsResolver;
import netdiag.http.HttpRequestBuilder;
import netdiag.http.HttpResponse;
import netdiag.http.HttpResponseReader;
import netdiag.url.RequestTarget;
import netdiag.url.UrlParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;

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

                InputStream input = socket.getInputStream();
                HttpResponse response;
                try {
                    response = HttpResponseReader.readResponse(input);
                } catch (SocketTimeoutException exception) {
                    printError(
                            "HTTP",
                            "timeout",
                            "timed out while waiting for HTTP response headers",
                            "check whether the server sends a response"
                    );
                    return;
                } catch (IOException exception) {
                    printError(
                            "HTTP",
                            "framing_error",
                            "invalid or incomplete HTTP response framing: "
                                    + exception.getMessage(),
                            "inspect the status line, headers, and body framing"
                    );
                    return;
                }

                String httpStatus = "HTTP " + response.getStatusCode();
                if (!response.getReasonPhrase().isBlank()) {
                    httpStatus += " " + response.getReasonPhrase();
                }

                byte[] decodedBody;
                try {
                    decodedBody = ContentEncodingDecoder.decode(response);
                } catch (IOException exception) {
                    printError(
                            "DECODE",
                            "decode_error",
                            "could not decode Content-Encoding: "
                                    + exception.getMessage(),
                            "inspect Content-Encoding and the encoded response body"
                    );
                    return;
                }

                Charset charset;
                try {
                    charset = CharsetResolver.resolve(response);
                } catch (IllegalArgumentException exception) {
                    printError(
                            "DECODE",
                            "decode_error",
                            "could not determine response charset: "
                                    + exception.getMessage(),
                            "inspect the charset parameter in Content-Type"
                    );
                    return;
                }

                String bodyText;
                try {
                    bodyText = TextDecoder.decode(decodedBody, charset);
                } catch (CharacterCodingException exception) {
                    printError(
                            "DECODE",
                            "decode_error",
                            "response body is not valid " + charset.name(),
                            "check whether the body matches the declared charset"
                    );
                    return;
                }

                printSuccess(
                        "HTTP",
                        "http_ok",
                        "received " + httpStatus
                                + " with a complete response body of "
                                + response.getBody().length + " bytes"
                );
                System.out.println("BODY: " + JsonStringEncoder.encode(bodyText));
            }
        } catch (UnknownHostException exception) {
            printError(
                    "DNS",
                    "dns_error",
                    "host " + host + " could not be resolved",
                    "check the hostname and DNS settings"
            );
        } catch (SocketTimeoutException exception) {
            printError(
                    "CONNECT",
                    "timeout",
                    "connection to " + host + ":" + port + " timed out",
                    "check that the server is reachable"
            );
        } catch (ConnectException | NoRouteToHostException exception) {
            printError(
                    "CONNECT",
                    "connect_error",
                    "connection to " + host + ":" + port + " failed: "
                            + exception.getMessage(),
                    "check that the server is running and the port is correct"
            );
        } catch (IOException exception) {
            printError(
                    "CONNECT",
                    "connect_error",
                    "connection to " + host + ":" + port + " failed: "
                            + exception.getMessage(),
                    "inspect the network connection"
            );
        } catch (IllegalArgumentException exception) {
            System.err.printf("Invalid URL: %s%n", exception.getMessage());
        }
    }

    private static void printError(
            String stage,
            String classification,
            String fact,
            String next
    ) {
        DiagnosticPrinter.print(new DiagnosticResult(
                stage,
                "error",
                classification,
                fact,
                next
        ));
    }

    private static void printSuccess(
            String stage,
            String classification,
            String fact
    ) {
        DiagnosticPrinter.print(new DiagnosticResult(
                stage,
                "ok",
                classification,
                fact,
                ""
        ));
    }
}
