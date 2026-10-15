package netdiag.tls;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Вручную проверяет имя хоста по полям X.509-сертификата сервера.
 */
public final class CertificateHostnameVerifier {
    private static final int DNS_NAME_TYPE = 2;
    private static final int IP_ADDRESS_TYPE = 7;

    private CertificateHostnameVerifier() {
    }

    public static HostnameVerificationResult verify(String requestedHost, SSLSession session)
            throws SSLPeerUnverifiedException, CertificateParsingException {
        if (requestedHost == null || requestedHost.isBlank()) {
            throw new IllegalArgumentException("Requested host must not be empty");
        }
        if (session == null) {
            throw new IllegalArgumentException("TLS session must not be null");
        }

        Certificate[] peerCertificates = session.getPeerCertificates();
        if (peerCertificates.length == 0
                || !(peerCertificates[0] instanceof X509Certificate certificate)) {
            throw new CertificateParsingException(
                    "TLS peer did not provide an X.509 certificate"
            );
        }

        String host = removeIpv6Brackets(requestedHost);
        byte[] requestedIp = parseIpLiteral(host);
        boolean ipRequest = requestedIp != null;
        boolean matched = false;
        boolean hasRelevantSubjectAlternativeName = false;
        List<String> certificateNames = new ArrayList<>();

        Collection<List<?>> subjectAlternativeNames =
                certificate.getSubjectAlternativeNames();

        if (subjectAlternativeNames != null) {
            for (List<?> entry : subjectAlternativeNames) {
                if (entry.size() < 2 || !(entry.get(0) instanceof Integer type)) {
                    continue;
                }

                Object value = entry.get(1);
                if (type == DNS_NAME_TYPE && value instanceof String dnsName) {
                    certificateNames.add("DNS:" + dnsName);
                    if (!ipRequest) {
                        hasRelevantSubjectAlternativeName = true;
                        if (matchesDnsName(host, dnsName)) {
                            matched = true;
                        }
                    }
                } else if (type == IP_ADDRESS_TYPE && value instanceof String ipAddress) {
                    certificateNames.add("IP:" + ipAddress);
                    if (ipRequest) {
                        hasRelevantSubjectAlternativeName = true;
                        byte[] certificateIp = parseIpLiteral(ipAddress);
                        if (certificateIp != null
                                && Arrays.equals(requestedIp, certificateIp)) {
                            matched = true;
                        }
                    }
                }
            }
        }

        if (!ipRequest && !hasRelevantSubjectAlternativeName) {
            String commonName = readCommonName(certificate);
            if (commonName != null) {
                certificateNames.add("CN:" + commonName);
                matched = matchesDnsName(host, commonName);
            }
        }

        return new HostnameVerificationResult(matched, certificateNames);
    }

    private static boolean matchesDnsName(String host, String certificateName) {
        String normalizedHost = normalizeDnsName(host);
        String normalizedCertificateName = normalizeDnsName(certificateName);

        if (!normalizedCertificateName.startsWith("*.")) {
            return normalizedHost.equals(normalizedCertificateName);
        }

        String suffix = normalizedCertificateName.substring(1);
        if (!normalizedHost.endsWith(suffix)) {
            return false;
        }

        String wildcardValue = normalizedHost.substring(
                0,
                normalizedHost.length() - suffix.length()
        );
        return !wildcardValue.isEmpty() && !wildcardValue.contains(".");
    }

    private static String normalizeDnsName(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String readCommonName(X509Certificate certificate)
            throws CertificateParsingException {
        try {
            LdapName subjectName = new LdapName(
                    certificate.getSubjectX500Principal().getName()
            );
            for (Rdn component : subjectName.getRdns()) {
                if (component.getType().equalsIgnoreCase("CN")) {
                    return String.valueOf(component.getValue());
                }
            }
            return null;
        } catch (InvalidNameException exception) {
            CertificateParsingException parsingException = new CertificateParsingException(
                    "Invalid certificate subject name"
            );
            parsingException.initCause(exception);
            throw parsingException;
        }
    }

    private static byte[] parseIpLiteral(String value) {
        String candidate = removeIpv6Brackets(value);
        if (!isIpv4Literal(candidate) && !candidate.contains(":")) {
            return null;
        }

        try {
            return InetAddress.getByName(candidate).getAddress();
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private static boolean isIpv4Literal(String value) {
        String[] components = value.split("\\.", -1);
        if (components.length != 4) {
            return false;
        }

        for (String component : components) {
            if (component.isEmpty() || component.length() > 3) {
                return false;
            }
            for (int index = 0; index < component.length(); index++) {
                if (!Character.isDigit(component.charAt(index))) {
                    return false;
                }
            }
            if (Integer.parseInt(component) > 255) {
                return false;
            }
        }
        return true;
    }

    private static String removeIpv6Brackets(String value) {
        if (value.length() >= 2 && value.startsWith("[") && value.endsWith("]")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
