package de.mrsimplejs.simplegeo;

final class NameAttempt {
    private final String name;
    private final long time;

    NameAttempt(String name, long time) {
        this.name = name == null ? "" : name;
        this.time = time;
    }

    String name() {
        return name;
    }

    long time() {
        return time;
    }
}
