package de.mrsimplejs.simplegeo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class AntiBotTracker {
    enum Result { ALLOWED, MULTIPLE_NAMES, TOO_MANY_JOINS }
    private static final long WINDOW_MS = 20_000L;
    private final Map<String, Deque<Long>> joins = new HashMap<>();
    private final Map<String, Deque<NameAttempt>> names = new HashMap<>();

    Result record(String ip, String name, long now) {
        Deque<NameAttempt> nameAttempts = names.computeIfAbsent(ip, key -> new ArrayDeque<>());
        while (!nameAttempts.isEmpty() && now - nameAttempts.peekFirst().time() > WINDOW_MS) nameAttempts.pollFirst();
        nameAttempts.addLast(new NameAttempt(name, now));
        Set<String> distinct = new HashSet<>();
        nameAttempts.forEach(attempt -> distinct.add(attempt.name().toLowerCase(Locale.ROOT)));
        if (distinct.size() >= 2) { nameAttempts.clear(); return Result.MULTIPLE_NAMES; }

        Deque<Long> joinAttempts = joins.computeIfAbsent(ip, key -> new ArrayDeque<>());
        while (!joinAttempts.isEmpty() && now - joinAttempts.peekFirst() > WINDOW_MS) joinAttempts.pollFirst();
        joinAttempts.addLast(now);
        if (joinAttempts.size() >= 5) { joinAttempts.clear(); return Result.TOO_MANY_JOINS; }
        return Result.ALLOWED;
    }
}
