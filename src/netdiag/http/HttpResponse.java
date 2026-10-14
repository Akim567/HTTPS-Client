package netdiag.http;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Хранит разобранную status line, заголовки и тело HTTP-ответа.
 */
public final class HttpResponse {
    private final String httpVersion;
    private final int statusCode;
    private final String reasonPhrase;
    private final Map<String, List<String>> headers;
    private final byte[] body;

    public HttpResponse(
            String httpVersion,
            int statusCode,
            String reasonPhrase,
            Map<String, List<String>> headers,
            byte[] body
    ) {
        this.httpVersion = httpVersion;
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;

        Map<String, List<String>> headersCopy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            headersCopy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.headers = Collections.unmodifiableMap(headersCopy);
        this.body = Objects.requireNonNull(body, "body must not be null").clone();
    }

    public String getHttpVersion() {
        return httpVersion;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getReasonPhrase() {
        return reasonPhrase;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public List<String> getHeaderValues(String name) {
        if (name == null) {
            return List.of();
        }

        return headers.getOrDefault(name.toLowerCase(Locale.ROOT), List.of());
    }

    public byte[] getBody() {
        return body.clone();
    }
}
