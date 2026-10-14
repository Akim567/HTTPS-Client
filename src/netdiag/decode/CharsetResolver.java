package netdiag.decode;

import netdiag.http.HttpResponse;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Извлекает кодировку текста из параметра charset заголовка Content-Type.
 */
public final class CharsetResolver {
    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    private CharsetResolver() {
    }

    public static Charset resolve(HttpResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("HTTP response must not be null");
        }

        List<String> contentTypes = response.getHeaderValues("Content-Type");
        if (contentTypes.isEmpty()) {
            return DEFAULT_CHARSET;
        }

        if (contentTypes.size() != 1) {
            throw new IllegalArgumentException(
                    "HTTP response contains multiple Content-Type headers"
            );
        }

        String[] parts = contentTypes.get(0).split(";", -1);
        String charsetName = null;

        for (int index = 1; index < parts.length; index++) {
            String parameter = parts[index].trim();
            if (parameter.isEmpty()) {
                throw new IllegalArgumentException("Content-Type contains an empty parameter");
            }

            int equalsIndex = parameter.indexOf('=');
            if (equalsIndex <= 0) {
                throw new IllegalArgumentException(
                        "Invalid Content-Type parameter: " + parameter
                );
            }

            String name = parameter.substring(0, equalsIndex)
                    .trim()
                    .toLowerCase(Locale.ROOT);
            String value = parameter.substring(equalsIndex + 1).trim();

            if (!name.equals("charset")) {
                continue;
            }

            if (charsetName != null) {
                throw new IllegalArgumentException(
                        "Content-Type contains multiple charset parameters"
                );
            }

            charsetName = removeOptionalQuotes(value);
            if (charsetName.isEmpty()) {
                throw new IllegalArgumentException("Content-Type charset must not be empty");
            }
        }

        if (charsetName == null) {
            return DEFAULT_CHARSET;
        }

        return Charset.forName(charsetName);
    }

    private static String removeOptionalQuotes(String value) {
        if (value.startsWith("\"")) {
            if (value.length() < 2 || !value.endsWith("\"")) {
                throw new IllegalArgumentException(
                        "Content-Type charset has an unterminated quoted value"
                );
            }

            return value.substring(1, value.length() - 1);
        }

        if (value.endsWith("\"")) {
            throw new IllegalArgumentException(
                    "Content-Type charset has an unexpected closing quote"
            );
        }

        return value;
    }
}
