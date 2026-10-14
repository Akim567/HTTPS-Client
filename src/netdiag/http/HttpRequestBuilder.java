package netdiag.http;

import netdiag.url.RequestTarget;

import java.nio.charset.StandardCharsets;

/**
 * Собирает HTTP/1.1 GET-запрос вручную, без готового HTTP-клиента.
 */
public final class HttpRequestBuilder {
    private static final String CRLF = "\r\n";

    private HttpRequestBuilder() {
    }

    public static byte[] buildGetRequest(RequestTarget target) {
        if (target == null) {
            throw new IllegalArgumentException("Request target must not be null");
        }

        StringBuilder request = new StringBuilder();
        request.append("GET ")
                .append(buildRequestTarget(target))
                .append(" HTTP/1.1")
                .append(CRLF);

        request.append("Host: ")
                .append(buildHostHeader(target))
                .append(CRLF);

        request.append("Accept: */*").append(CRLF);
        request.append("Connection: close").append(CRLF);
        request.append(CRLF);

        return request.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static String buildRequestTarget(RequestTarget target) {
        String path = target.getPath();
        String query = target.getQuery();

        if (query == null) {
            return path;
        }

        return path + "?" + query;
    }

    private static String buildHostHeader(RequestTarget target) {
        String scheme = target.getScheme();
        String host = target.getHost();
        int port = target.getPort();

        boolean isDefaultPort =
                scheme.equals("http") && port == 80
                        || scheme.equals("https") && port == 443;

        if (isDefaultPort) {
            return host;
        }

        return host + ":" + port;
    }
}
