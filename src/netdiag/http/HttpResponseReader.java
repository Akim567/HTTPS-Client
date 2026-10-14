package netdiag.http;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Вручную читает status line и заголовки HTTP/1.1-ответа.
 */
public final class HttpResponseReader {
    private static final byte[] HEADER_TERMINATOR = {'\r', '\n', '\r', '\n'};
    private static final int MAX_HEADER_BYTES = 64 * 1024;

    private HttpResponseReader() {
    }

    public static HttpResponse readHead(InputStream input) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("Input stream must not be null");
        }

        byte[] headerBytes = readHeaderBlock(input);
        String headerBlock = new String(headerBytes, StandardCharsets.ISO_8859_1);
        String[] lines = headerBlock.split("\r\n", -1);

        if (lines.length == 0 || lines[0].isBlank()) {
            throw new IOException("HTTP response does not contain a status line");
        }

        String[] statusParts = lines[0].split(" ", 3);
        if (statusParts.length < 2 || !statusParts[0].startsWith("HTTP/")) {
            throw new IOException("Invalid HTTP status line: " + lines[0]);
        }

        int statusCode;
        try {
            statusCode = Integer.parseInt(statusParts[1]);
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid HTTP status code: " + statusParts[1], exception);
        }

        if (statusParts[1].length() != 3 || statusCode < 100 || statusCode > 999) {
            throw new IOException("Invalid HTTP status code: " + statusParts[1]);
        }

        String reasonPhrase = statusParts.length == 3 ? statusParts[2] : "";
        Map<String, List<String>> headers = parseHeaders(lines);

        return new HttpResponse(statusParts[0], statusCode, reasonPhrase, headers);
    }

    private static byte[] readHeaderBlock(InputStream input) throws IOException {
        ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
        int matchedTerminatorBytes = 0;

        while (matchedTerminatorBytes < HEADER_TERMINATOR.length) {
            int nextByte = input.read();
            if (nextByte == -1) {
                throw new EOFException("Connection closed before HTTP headers were complete");
            }

            headerBytes.write(nextByte);
            if (headerBytes.size() > MAX_HEADER_BYTES) {
                throw new IOException("HTTP headers exceed " + MAX_HEADER_BYTES + " bytes");
            }

            if (nextByte == HEADER_TERMINATOR[matchedTerminatorBytes]) {
                matchedTerminatorBytes++;
            } else {
                matchedTerminatorBytes = nextByte == HEADER_TERMINATOR[0] ? 1 : 0;
            }
        }

        return headerBytes.toByteArray();
    }

    private static Map<String, List<String>> parseHeaders(String[] lines) throws IOException {
        Map<String, List<String>> headers = new LinkedHashMap<>();

        for (int index = 1; index < lines.length; index++) {
            String line = lines[index];
            if (line.isEmpty()) {
                break;
            }

            int colonIndex = line.indexOf(':');
            if (colonIndex <= 0) {
                throw new IOException("Invalid HTTP header: " + line);
            }

            String name = line.substring(0, colonIndex).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colonIndex + 1).trim();
            if (name.isEmpty()) {
                throw new IOException("HTTP header name must not be empty");
            }

            headers.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
        }

        return headers;
    }
}
