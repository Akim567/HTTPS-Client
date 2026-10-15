package netdiag.tls;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * Создаёт TLS-контекст, доверяющий CA из переданного файла.
 */
public final class TlsContextFactory {
    private TlsContextFactory() {
    }

    public static SSLContext create(Path caCertificatePath)
            throws IOException, GeneralSecurityException {
        if (caCertificatePath == null) {
            throw new IllegalArgumentException("CA certificate path must not be null");
        }

        X509Certificate caCertificate = loadCertificate(caCertificatePath);

        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("trusted-ca", caCertificate);

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
        );
        trustManagerFactory.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
        return sslContext;
    }

    private static X509Certificate loadCertificate(Path certificatePath)
            throws IOException, GeneralSecurityException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");

        try (InputStream input = Files.newInputStream(certificatePath)) {
            return (X509Certificate) certificateFactory.generateCertificate(input);
        }
    }
}
