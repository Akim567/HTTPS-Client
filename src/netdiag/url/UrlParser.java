package netdiag.url;

import java.net.URI;
import java.util.Locale;

/**
 * Преобразует строковый URL в данные, необходимые сетевым этапам.
 */
public final class UrlParser {
    private UrlParser() {
    }

    public static RequestTarget create(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL must not be empty");
        }

        URI uri = URI.create(url);

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("URL must contain a scheme");
        }

        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("URL scheme must be http or https");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL must contain a host");
        }

        int port = uri.getPort();
        if (port == -1) {
            port = scheme.equals("http") ? 80 : 443;
        } else if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("URL port must be between 1 and 65535");
        }

        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }

        String query = uri.getRawQuery();

        return new RequestTarget(scheme, host, port, path, query);
    }
}
