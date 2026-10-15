package netdiag;

import netdiag.cli.ArgumentsParser;
import netdiag.cli.CommandLineArguments;
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
import netdiag.json.JsonParseException;
import netdiag.json.JsonValidator;
import netdiag.tls.CertificateHostnameVerifier;
import netdiag.tls.HostnameVerificationResult;
import netdiag.tls.TlsConnector;
import netdiag.tls.TlsContextFactory;
import netdiag.tls.TlsFailureDescriber;
import netdiag.url.RequestTarget;
import netdiag.url.UrlParser;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
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
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateParsingException;

public class Main {
    public static void main(String[] args) {
        CommandLineArguments commandLineArguments;
        try {
            commandLineArguments = ArgumentsParser.parse(args);
        } catch (IllegalArgumentException exception) {
            System.err.printf("Invalid arguments: %s%n", exception.getMessage());
            System.err.println(ArgumentsParser.usage());
            return;
        }

        String host = null;
        int port = -1;

        try {
            RequestTarget target = UrlParser.create(commandLineArguments.getUrl());
            host = target.getHost();
            port = target.getPort();

            Path caCertificatePath = commandLineArguments.getCaCertificatePath();
            if (target.getScheme().equals("https") && caCertificatePath == null) {
                System.err.println("Invalid arguments: --ca is required for HTTPS");
                System.err.println(ArgumentsParser.usage());
                return;
            }

            InetAddress[] addresses = DnsResolver.resolve(host);

            try (Socket tcpSocket = TcpConnector.connect(
                    addresses[0],
                    port,
                    commandLineArguments.getTimeoutMillis()
            )) {
                Socket socket = tcpSocket;

                if (target.getScheme().equals("https")) {
                    try {
                        SSLContext sslContext = TlsContextFactory.create(caCertificatePath);
                        SSLSocket tlsSocket = TlsConnector.connect(
                                tcpSocket,
                                host,
                                port,
                                commandLineArguments.getTimeoutMillis(),
                                sslContext
                        );

                        HostnameVerificationResult hostnameResult =
                                CertificateHostnameVerifier.verify(
                                        host,
                                        tlsSocket.getSession()
                                );
                        if (!hostnameResult.isMatched()) {
                            printError(
                                    "TLS",
                                    "cert_invalid",
                                    "requested host " + host
                                            + " does not match certificate names "
                                            + hostnameResult.getCertificateNames(),
                                    "use a hostname listed in the server certificate"
                            );
                            return;
                        }

                        socket = tlsSocket;
                    } catch (SocketTimeoutException exception) {
                        printError(
                                "TLS",
                                "timeout",
                                "TLS handshake with " + host + ":" + port + " timed out",
                                "check whether the server completes the TLS handshake"
                        );
                        return;
                    } catch (SSLHandshakeException exception) {
                        printError(
                                "TLS",
                                "tls_error",
                                "TLS handshake failed: "
                                        + TlsFailureDescriber.describe(exception),
                                "inspect the certificate trust chain and validity period"
                        );
                        return;
                    } catch (SSLPeerUnverifiedException
                             | CertificateParsingException exception) {
                        printError(
                                "TLS",
                                "cert_invalid",
                                "could not verify requested host " + host + ": "
                                        + exception.getMessage(),
                                "inspect the names in the server certificate"
                        );
                        return;
                    } catch (GeneralSecurityException | IOException exception) {
                        printError(
                                "TLS",
                                "tls_error",
                                "could not establish TLS: "
                                        + TlsFailureDescriber.describe(exception),
                                "check the CA certificate and TLS configuration"
                        );
                        return;
                    }
                }

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

                if (JsonValidator.isJsonResponse(response)) {
                    try {
                        JsonValidator.validate(bodyText);
                    } catch (JsonParseException exception) {
                        printError(
                                "PARSE",
                                "parse_error",
                                "response body is not valid JSON: "
                                        + exception.getMessage(),
                                "inspect the JSON syntax"
                        );
                        return;
                    }
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
