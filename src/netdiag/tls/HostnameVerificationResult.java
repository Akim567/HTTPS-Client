package netdiag.tls;

import java.util.List;

/**
 * Результат ручного сравнения запрошенного хоста с именами сертификата.
 */
public final class HostnameVerificationResult {
    private final boolean matched;
    private final List<String> certificateNames;

    public HostnameVerificationResult(boolean matched, List<String> certificateNames) {
        this.matched = matched;
        this.certificateNames = List.copyOf(certificateNames);
    }

    public boolean isMatched() {
        return matched;
    }

    public List<String> getCertificateNames() {
        return certificateNames;
    }
}
