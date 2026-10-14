package netdiag.decode;

import netdiag.http.HttpResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * Восстанавливает байты представления по заголовку Content-Encoding.
 */
public final class ContentEncodingDecoder {
    private static final int MAX_DECODED_BODY_BYTES = 64 * 1024 * 1024;

    private ContentEncodingDecoder() {
    }

    public static byte[] decode(HttpResponse response) throws IOException {
        if (response == null) {
            throw new IllegalArgumentException("HTTP response must not be null");
        }

        byte[] decodedBody = response.getBody();
        List<String> codings = parseCodings(response.getHeaderValues("Content-Encoding"));

        for (int index = codings.size() - 1; index >= 0; index--) {
            String coding = codings.get(index);
            if (coding.equals("gzip")) {
                decodedBody = decodeGzip(decodedBody);
            } else {
                throw new IOException("Unsupported Content-Encoding: " + coding);
            }
        }

        return decodedBody;
    }

    private static List<String> parseCodings(List<String> values) throws IOException {
        List<String> codings = new ArrayList<>();

        for (String value : values) {
            for (String coding : value.split(",", -1)) {
                String normalizedCoding = coding.trim().toLowerCase(Locale.ROOT);
                if (normalizedCoding.isEmpty()) {
                    throw new IOException("Content-Encoding contains an empty coding");
                }
                codings.add(normalizedCoding);
            }
        }

        return codings;
    }

    private static byte[] decodeGzip(byte[] encodedBody) throws IOException {
        try (
                ByteArrayInputStream encodedInput = new ByteArrayInputStream(encodedBody);
                GZIPInputStream gzipInput = new GZIPInputStream(encodedInput);
                ByteArrayOutputStream decodedOutput = new ByteArrayOutputStream()
        ) {
            byte[] buffer = new byte[8 * 1024];
            int bytesRead;

            while ((bytesRead = gzipInput.read(buffer)) != -1) {
                if (bytesRead > MAX_DECODED_BODY_BYTES - decodedOutput.size()) {
                    throw new IOException(
                            "Decoded gzip body exceeds "
                                    + MAX_DECODED_BODY_BYTES + " bytes"
                    );
                }

                decodedOutput.write(buffer, 0, bytesRead);
            }

            return decodedOutput.toByteArray();
        } catch (IOException exception) {
            throw new IOException(
                    "gzip decompression failed: " + describe(exception),
                    exception
            );
        }
    }

    private static String describe(IOException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
