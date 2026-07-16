package de.mrsimplejs.simplegeo;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

final class HttpSupport {
    private HttpSupport() {
    }

    static HttpURLConnection open(String rawUrl) throws IOException {
        return (HttpURLConnection) URI.create(rawUrl).toURL().openConnection();
    }

    static boolean isRetryable(Exception exception) {
        return exception instanceof SocketException
            || exception instanceof javax.net.ssl.SSLException
            || exception instanceof SocketTimeoutException
            || exception instanceof EOFException;
    }

    static boolean isConnectionReset(Exception exception) {
        String message = exception == null ? null : exception.getMessage();
        return exception instanceof SocketException
            && message != null
            && message.toLowerCase(Locale.ROOT).contains("connection reset");
    }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    static String readText(InputStream input, int maxChars) throws IOException {
        if (input == null) return null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder result = new StringBuilder();
            char[] buffer = new char[1024];
            int read;
            while (result.length() < maxChars && (read = reader.read(buffer)) != -1) {
                result.append(buffer, 0, Math.min(read, maxChars - result.length()));
            }
            return result.toString();
        }
    }

    static byte[] readBytes(InputStream input) throws IOException {
        if (input == null) return new byte[0];
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        input.transferTo(output);
        return output.toByteArray();
    }

    static String jsonString(String body, String key) {
        if (body == null || key == null) return null;
        var matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"(.*?)\"",
            Pattern.CASE_INSENSITIVE).matcher(body);
        return matcher.find() ? unescapeJson(matcher.group(1)) : null;
    }

    static Boolean jsonBoolean(String body, String key) {
        if (body == null || key == null) return null;
        var matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)",
            Pattern.CASE_INSENSITIVE).matcher(body);
        return matcher.find() ? Boolean.parseBoolean(matcher.group(1)) : null;
    }

    private static String unescapeJson(String value) {
        if (value == null || value.isEmpty()) return value;
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                switch (next) {
                    case '"', '\\', '/' -> out.append(next);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (i + 4 < value.length()) {
                            try {
                                out.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
                                i += 4;
                            } catch (NumberFormatException e) {
                                out.append("\\u");
                            }
                        } else out.append("\\u");
                    }
                    default -> out.append('\\').append(next);
                }
            } else out.append(c);
        }
        return out.toString();
    }
}
