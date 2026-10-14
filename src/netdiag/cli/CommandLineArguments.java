package netdiag.cli;

import java.nio.file.Path;

public final class CommandLineArguments {
    private final String url;
    private final Path caCertificatePath;
    private final int timeoutMillis;

    CommandLineArguments(String url, Path caCertificatePath, int timeoutMillis) {
        this.url = url;
        this.caCertificatePath = caCertificatePath;
        this.timeoutMillis = timeoutMillis;
    }

    public String getUrl() {
        return url;
    }

    public Path getCaCertificatePath() {
        return caCertificatePath;
    }

    public int getTimeoutMillis() {
        return timeoutMillis;
    }
}
