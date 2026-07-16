package de.mrsimplejs.simplegeo;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

final class NetworkUtils {
    private NetworkUtils() {
    }

    static IpRange parseCidr(String cidr) {
        if (cidr == null || cidr.isBlank()) {
            return null;
        }
        String trimmed = cidr.trim();
        String[] parts = trimmed.split("/", 2);
        if (parts.length != 2) {
            return null;
        }
        try {
            InetAddress address = InetAddress.getByName(parts[0].trim());
            int prefix = Integer.parseInt(parts[1].trim());
            byte[] bytes = address.getAddress();
            int totalBits = bytes.length * 8;
            if (prefix < 0 || prefix > totalBits) {
                return null;
            }
            BigInteger ipValue = new BigInteger(1, bytes);
            BigInteger mask = prefix == 0
                ? BigInteger.ZERO
                : BigInteger.ONE.shiftLeft(prefix).subtract(BigInteger.ONE).shiftLeft(totalBits - prefix);
            BigInteger start = ipValue.and(mask);
            BigInteger end = start.add(BigInteger.ONE.shiftLeft(totalBits - prefix).subtract(BigInteger.ONE));
            return new IpRange(start, end, trimmed);
        } catch (Exception e) {
            return null;
        }
    }

    static BigInteger resolveIpValue(String ip) {
        try {
            return new BigInteger(1, InetAddress.getByName(ip).getAddress());
        } catch (Exception e) {
            return null;
        }
    }

    static BigInteger parseBigInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigInteger(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        if (line == null) {
            return fields;
        }
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes && c == '"') {
                if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = false;
                }
            } else if (!inQuotes && c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else if (!inQuotes && c == '"') {
                inQuotes = true;
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    static String centerLegacyText(String message, int width) {
        if (message == null) {
            return "";
        }
        String visible = message.replaceAll("(?i)&[0-9A-FK-ORX]", "");
        int padding = Math.max(0, (width - visible.length()) / 2);
        return " ".repeat(padding) + message;
    }
}
