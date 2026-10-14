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
 * Вручную читает HTTP/1.1-ответ и определяет границы тела по
 * Content-Length или Transfer-Encoding: chunked.
 */
public final class HttpResponseReader {
    private static final byte[] HEADER_TERMINATOR = {'\r', '\n', '\r', '\n'};
    private static final int MAX_HEADER_BYTES = 64 * 1024;
    private static final int MAX_CHUNK_LINE_BYTES = 8 * 1024;
    private static final int MAX_BODY_BYTES = 64 * 1024 * 1024;

    private HttpResponseReader() {
    }

    public static HttpResponse readResponse(InputStream input) throws IOException {
        HttpResponse responseHead = readHead(input);
        byte[] body;

        if (usesChunkedTransferEncoding(responseHead)) {
            if (!responseHead.getHeaderValues("Content-Length").isEmpty()) {
                throw new IOException(
                        "HTTP response contains both Transfer-Encoding and Content-Length"
                );
            }

            body = readChunkedBody(input);
        } else {
            int contentLength = parseContentLength(responseHead);
            body = readFixedLengthBody(input, contentLength);
        }

        return new HttpResponse(
                responseHead.getHttpVersion(),
                responseHead.getStatusCode(),
                responseHead.getReasonPhrase(),
                responseHead.getHeaders(),
                body
        );
    }

    private static boolean usesChunkedTransferEncoding(HttpResponse response)
            throws IOException {
        List<String> values = response.getHeaderValues("Transfer-Encoding");
        if (values.isEmpty()) {
            return false;
        }

        List<String> codings = new ArrayList<>();
        for (String value : values) {
            for (String coding : value.split(",", -1)) {
                String normalizedCoding = coding.trim().toLowerCase(Locale.ROOT);
                if (normalizedCoding.isEmpty()) {
                    throw new IOException("Transfer-Encoding contains an empty coding");
                }
                codings.add(normalizedCoding);
            }
        }

        if (codings.size() != 1 || !codings.get(0).equals("chunked")) {
            throw new IOException("Unsupported Transfer-Encoding: " + String.join(", ", codings));
        }

        return true;
    }

    private static byte[] readChunkedBody(InputStream input) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();

        while (true) {
            String sizeLine = readCrlfLine(input, MAX_CHUNK_LINE_BYTES, "chunk size");
            int extensionIndex = sizeLine.indexOf(';');
            String sizeText = (extensionIndex >= 0
                    ? sizeLine.substring(0, extensionIndex)
                    : sizeLine).trim();

            int chunkSize = parseChunkSize(sizeText);
            if (chunkSize == 0) {
                readTrailers(input);
                return body.toByteArray();
            }

            if (chunkSize > MAX_BODY_BYTES - body.size()) {
                throw new IOException(
                        "Chunked response body exceeds " + MAX_BODY_BYTES + " bytes"
                );
            }

            copyExactly(input, body, chunkSize);
            requireCrlf(input, "chunk data");
        }
    }

    private static int parseChunkSize(String sizeText) throws IOException {
        if (sizeText.isEmpty()) {
            throw new IOException("Chunk size must not be empty");
        }

        long size = 0;
        for (int index = 0; index < sizeText.length(); index++) {
            int digit = Character.digit(sizeText.charAt(index), 16);
            if (digit == -1) {
                throw new IOException("Invalid hexadecimal chunk size: " + sizeText);
            }

            size = size * 16 + digit;
            if (size > MAX_BODY_BYTES) {
                throw new IOException("Chunk size is too large: " + sizeText);
            }
        }

        return (int) size;
    }

    private static void copyExactly(
            InputStream input,
            ByteArrayOutputStream destination,
            int byteCount
    ) throws IOException {
        byte[] buffer = new byte[Math.min(8 * 1024, byteCount)];
        int remaining = byteCount;

        while (remaining > 0) {
            int bytesRead = input.read(buffer, 0, Math.min(buffer.length, remaining));
            if (bytesRead == -1) {
                throw new EOFException(
                        "Connection closed with " + remaining
                                + " bytes missing from the current chunk"
                );
            }

            destination.write(buffer, 0, bytesRead);
            remaining -= bytesRead;
        }
    }

    private static void requireCrlf(InputStream input, String after) throws IOException {
        int carriageReturn = input.read();
        int lineFeed = input.read();

        if (carriageReturn == -1 || lineFeed == -1) {
            throw new EOFException("Connection closed before CRLF after " + after);
        }

        if (carriageReturn != '\r' || lineFeed != '\n') {
            throw new IOException("Expected CRLF after " + after);
        }
    }

    private static void readTrailers(InputStream input) throws IOException {
        int trailerBytes = 0;

        while (true) {
            String trailer = readCrlfLine(input, MAX_HEADER_BYTES, "chunk trailers");
            trailerBytes += trailer.length() + 2;
            if (trailerBytes > MAX_HEADER_BYTES) {
                throw new IOException(
                        "HTTP chunk trailers exceed " + MAX_HEADER_BYTES + " bytes"
                );
            }

            if (trailer.isEmpty()) {
                return;
            }

            if (trailer.indexOf(':') <= 0) {
                throw new IOException("Invalid HTTP trailer: " + trailer);
            }
        }
    }

    private static String readCrlfLine(
            InputStream input,
            int maxBytes,
            String description
    ) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();

        while (true) {
            int nextByte = input.read();
            if (nextByte == -1) {
                throw new EOFException(
                        "Connection closed before " + description + " line was complete"
                );
            }

            if (nextByte == '\r') {
                int lineFeed = input.read();
                if (lineFeed == -1) {
                    throw new EOFException(
                            "Connection closed before CRLF after " + description
                    );
                }
                if (lineFeed != '\n') {
                    throw new IOException("Expected LF after CR in " + description);
                }

                return line.toString(StandardCharsets.ISO_8859_1);
            }

            if (nextByte == '\n') {
                throw new IOException("Expected CR before LF in " + description);
            }

            line.write(nextByte);
            if (line.size() > maxBytes) {
                throw new IOException(description + " line exceeds " + maxBytes + " bytes");
            }
        }
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

        return new HttpResponse(
                statusParts[0],
                statusCode,
                reasonPhrase,
                headers,
                new byte[0]
        );
    }

    private static int parseContentLength(HttpResponse response) throws IOException {
        List<String> values = response.getHeaderValues("Content-Length");
        if (values.isEmpty()) {
            throw new IOException("HTTP response does not contain Content-Length");
        }

        if (values.size() != 1) {
            throw new IOException("HTTP response contains multiple Content-Length headers");
        }

        String value = values.get(0);
        if (value.isEmpty()) {
            throw new IOException("Content-Length must not be empty");
        }

        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                throw new IOException("Invalid Content-Length: " + value);
            }
        }

        long contentLength;
        try {
            contentLength = Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid Content-Length: " + value, exception);
        }

        if (contentLength > MAX_BODY_BYTES) {
            throw new IOException("Content-Length is too large: " + value);
        }

        return (int) contentLength;
    }

    private static byte[] readFixedLengthBody(InputStream input, int contentLength)
            throws IOException {
        byte[] body = new byte[contentLength];
        int offset = 0;

        while (offset < contentLength) {
            int bytesRead = input.read(body, offset, contentLength - offset);
            if (bytesRead == -1) {
                throw new EOFException(
                        "Connection closed after " + offset
                                + " of " + contentLength + " body bytes"
                );
            }

            offset += bytesRead;
        }

        return body;
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
