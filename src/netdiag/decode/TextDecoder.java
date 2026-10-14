package netdiag.decode;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;

/**
 * Строго преобразует байты тела ответа в текст.
 */
public final class TextDecoder {
    private TextDecoder() {
    }

    public static String decode(byte[] body, Charset charset)
            throws CharacterCodingException {
        if (body == null) {
            throw new IllegalArgumentException("Body must not be null");
        }
        if (charset == null) {
            throw new IllegalArgumentException("Charset must not be null");
        }

        return charset
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(body))
                .toString();
    }
}
