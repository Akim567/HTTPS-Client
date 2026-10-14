package netdiag.json;

import netdiag.http.HttpResponse;

import java.util.List;
import java.util.Locale;

/**
 * Проверяет JSON-грамматику, не изменяя исходный текст.
 */
public final class JsonValidator {
    private static final int MAX_NESTING_DEPTH = 256;

    private JsonValidator() {
    }

    public static boolean isJsonResponse(HttpResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("HTTP response must not be null");
        }

        List<String> contentTypes = response.getHeaderValues("Content-Type");
        if (contentTypes.isEmpty()) {
            return false;
        }

        String contentType = contentTypes.get(0);
        int parameterIndex = contentType.indexOf(';');
        String mediaType = (parameterIndex >= 0
                ? contentType.substring(0, parameterIndex)
                : contentType).trim().toLowerCase(Locale.ROOT);

        return mediaType.equals("application/json") || mediaType.endsWith("+json");
    }

    public static void validate(String json) throws JsonParseException {
        if (json == null) {
            throw new IllegalArgumentException("JSON text must not be null");
        }

        Parser parser = new Parser(json);
        parser.validateDocument();
    }

    private static final class Parser {
        private final String text;
        private int index;

        private Parser(String text) {
            this.text = text;
        }

        private void validateDocument() throws JsonParseException {
            skipWhitespace();
            parseValue(0);
            skipWhitespace();

            if (!isAtEnd()) {
                throw error("Unexpected characters after the JSON value");
            }
        }

        private void parseValue(int depth) throws JsonParseException {
            if (isAtEnd()) {
                throw error("Expected a JSON value");
            }

            char character = current();
            switch (character) {
                case '{' -> parseObject(depth);
                case '[' -> parseArray(depth);
                case '"' -> parseString();
                case 't' -> parseLiteral("true");
                case 'f' -> parseLiteral("false");
                case 'n' -> parseLiteral("null");
                default -> {
                    if (character == '-' || isDigit(character)) {
                        parseNumber();
                    } else {
                        throw error("Expected a JSON value");
                    }
                }
            }
        }

        private void parseObject(int depth) throws JsonParseException {
            checkDepth(depth);
            expect('{');
            skipWhitespace();

            if (consumeIf('}')) {
                return;
            }

            while (true) {
                if (isAtEnd() || current() != '"') {
                    throw error("Expected a string object key");
                }
                parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                parseValue(depth + 1);
                skipWhitespace();

                if (consumeIf('}')) {
                    return;
                }

                expect(',');
                skipWhitespace();
                if (!isAtEnd() && current() == '}') {
                    throw error("Trailing comma in object");
                }
            }
        }

        private void parseArray(int depth) throws JsonParseException {
            checkDepth(depth);
            expect('[');
            skipWhitespace();

            if (consumeIf(']')) {
                return;
            }

            while (true) {
                parseValue(depth + 1);
                skipWhitespace();

                if (consumeIf(']')) {
                    return;
                }

                expect(',');
                skipWhitespace();
                if (!isAtEnd() && current() == ']') {
                    throw error("Trailing comma in array");
                }
            }
        }

        private void parseString() throws JsonParseException {
            expect('"');

            while (!isAtEnd()) {
                char character = text.charAt(index++);
                if (character == '"') {
                    return;
                }

                if (character == '\\') {
                    parseEscapeSequence();
                } else if (character < 0x20) {
                    throw errorAtPrevious("Unescaped control character in JSON string");
                } else if (Character.isHighSurrogate(character)) {
                    if (isAtEnd() || !Character.isLowSurrogate(text.charAt(index))) {
                        throw errorAtPrevious("High surrogate is not followed by a low surrogate");
                    }
                    index++;
                } else if (Character.isLowSurrogate(character)) {
                    throw errorAtPrevious("Unexpected low surrogate in JSON string");
                }
            }

            throw error("Unterminated JSON string");
        }

        private void parseEscapeSequence() throws JsonParseException {
            if (isAtEnd()) {
                throw error("Unterminated escape sequence in JSON string");
            }

            char escaped = text.charAt(index++);
            switch (escaped) {
                case '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> {
                    return;
                }
                case 'u' -> parseUnicodeEscape();
                default -> throw errorAtPrevious("Invalid escape sequence in JSON string");
            }
        }

        private void parseUnicodeEscape() throws JsonParseException {
            int codeUnit = parseFourHexDigits();
            if (Character.isHighSurrogate((char) codeUnit)) {
                if (index + 2 > text.length()
                        || text.charAt(index) != '\\'
                        || text.charAt(index + 1) != 'u') {
                    throw error("High surrogate escape is not followed by a low surrogate escape");
                }

                index += 2;
                int lowCodeUnit = parseFourHexDigits();
                if (!Character.isLowSurrogate((char) lowCodeUnit)) {
                    throw error("High surrogate escape is not followed by a low surrogate escape");
                }
            } else if (Character.isLowSurrogate((char) codeUnit)) {
                throw error("Unexpected low surrogate escape in JSON string");
            }
        }

        private int parseFourHexDigits() throws JsonParseException {
            if (index + 4 > text.length()) {
                throw error("Incomplete Unicode escape in JSON string");
            }

            int value = 0;
            for (int count = 0; count < 4; count++) {
                int digit = Character.digit(text.charAt(index++), 16);
                if (digit == -1) {
                    throw errorAtPrevious("Invalid hexadecimal digit in Unicode escape");
                }
                value = value * 16 + digit;
            }
            return value;
        }

        private void parseNumber() throws JsonParseException {
            consumeIf('-');
            if (isAtEnd()) {
                throw error("Expected a digit in JSON number");
            }

            if (consumeIf('0')) {
                if (!isAtEnd() && isDigit(current())) {
                    throw error("Leading zero in JSON number");
                }
            } else {
                if (!isDigitOneToNine(current())) {
                    throw error("Expected a digit in JSON number");
                }
                consumeDigits();
            }

            if (consumeIf('.')) {
                if (isAtEnd() || !isDigit(current())) {
                    throw error("Expected a digit after decimal point");
                }
                consumeDigits();
            }

            if (!isAtEnd() && (current() == 'e' || current() == 'E')) {
                index++;
                if (!isAtEnd() && (current() == '+' || current() == '-')) {
                    index++;
                }
                if (isAtEnd() || !isDigit(current())) {
                    throw error("Expected a digit in JSON exponent");
                }
                consumeDigits();
            }
        }

        private void parseLiteral(String literal) throws JsonParseException {
            for (int offset = 0; offset < literal.length(); offset++) {
                if (isAtEnd() || current() != literal.charAt(offset)) {
                    throw error("Invalid JSON literal; expected " + literal);
                }
                index++;
            }
        }

        private void consumeDigits() {
            while (!isAtEnd() && isDigit(current())) {
                index++;
            }
        }

        private void skipWhitespace() {
            while (!isAtEnd()) {
                char character = current();
                if (character == ' '
                        || character == '\t'
                        || character == '\r'
                        || character == '\n') {
                    index++;
                } else {
                    return;
                }
            }
        }

        private void checkDepth(int depth) throws JsonParseException {
            if (depth >= MAX_NESTING_DEPTH) {
                throw error("JSON nesting exceeds " + MAX_NESTING_DEPTH + " levels");
            }
        }

        private void expect(char expected) throws JsonParseException {
            if (isAtEnd() || current() != expected) {
                throw error("Expected '" + expected + "'");
            }
            index++;
        }

        private boolean consumeIf(char expected) {
            if (!isAtEnd() && current() == expected) {
                index++;
                return true;
            }
            return false;
        }

        private char current() {
            return text.charAt(index);
        }

        private boolean isAtEnd() {
            return index >= text.length();
        }

        private static boolean isDigit(char character) {
            return character >= '0' && character <= '9';
        }

        private static boolean isDigitOneToNine(char character) {
            return character >= '1' && character <= '9';
        }

        private JsonParseException error(String message) {
            return new JsonParseException(message, index);
        }

        private JsonParseException errorAtPrevious(String message) {
            return new JsonParseException(message, Math.max(0, index - 1));
        }
    }
}
