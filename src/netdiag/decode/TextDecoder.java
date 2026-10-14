package netdiag.decode;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Строго преобразует байты тела ответа в текст.
 */
public final class TextDecoder {
    private TextDecoder() {
    }

    public static String decodeUtf8(byte[] body) throws CharacterCodingException {
        if (body == null) {
            throw new IllegalArgumentException("Body must not be null");
        }

        return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(body))
                .toString();
    }
}
