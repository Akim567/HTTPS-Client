package netdiag.json;

/**
 * Сообщает позицию синтаксической ошибки в JSON-тексте.
 */
public final class JsonParseException extends Exception {
    public JsonParseException(String message, int zeroBasedPosition) {
        super(message + " at character " + (zeroBasedPosition + 1));
    }
}
