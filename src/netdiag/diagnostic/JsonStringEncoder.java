package netdiag.diagnostic;

/**
 * Представляет Java-строку как корректный строковый литерал JSON.
 */
public final class JsonStringEncoder {
    private JsonStringEncoder() {
    }

    public static String encode(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null");
        }

        StringBuilder result = new StringBuilder(value.length() + 2);
        result.append('"');

        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);

            switch (character) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 0x20) {
                        result.append(String.format("\\u%04x", (int) character));
                    } else {
                        result.append(character);
                    }
                }
            }
        }

        result.append('"');
        return result.toString();
    }
}
