package netdiag.tls;

import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;

/**
 * Извлекает конкретную причину ошибки из цепочки исключений TLS.
 */
public final class TlsFailureDescriber {
    private TlsFailureDescriber() {
    }

    public static String describe(Throwable exception) {
        if (exception == null) {
            return "unknown TLS failure";
        }

        String deepestMessage = null;
        Throwable current = exception;

        while (current != null) {
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                deepestMessage = message;
            }

            if (current instanceof CertificateExpiredException) {
                return "certificate expired: " + messageOrClassName(current);
            }
            if (current instanceof CertificateNotYetValidException) {
                return "certificate is not yet valid: " + messageOrClassName(current);
            }
            if (current instanceof CertPathValidatorException validatorException
                    && validatorException.getReason()
                    == CertPathValidatorException.BasicReason.EXPIRED) {
                return "certificate expired: " + messageOrClassName(current);
            }

            current = current.getCause();
        }

        if (deepestMessage != null) {
            return deepestMessage;
        }
        return exception.getClass().getSimpleName();
    }

    private static String messageOrClassName(Throwable exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }
}
