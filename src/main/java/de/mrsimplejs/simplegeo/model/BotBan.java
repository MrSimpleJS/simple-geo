package de.mrsimplejs.simplegeo;

final class BotBan {
    private final int id;
    private final String ip;
    private final String name;
    private final long createdAt;
    private final long expiresAt;

    BotBan(int id, String ip, String name, long createdAt, long expiresAt) {
        this.id = id;
        this.ip = ip;
        this.name = name == null ? "" : name;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    int id() {
        return id;
    }

    String ip() {
        return ip;
    }

    String name() {
        return name;
    }

    long createdAt() {
        return createdAt;
    }

    long expiresAt() {
        return expiresAt;
    }
}
