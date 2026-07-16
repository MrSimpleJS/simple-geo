package de.mrsimplejs.simplegeo;

import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class IspLookupService {
    private static final String ENDPOINT = "https://ipwho.is/";
    private static final String FIELDS = "success,message,connection.isp";
    private static final long LIMIT_BACKOFF_MS = TimeUnit.HOURS.toMillis(12);
    private static final long CACHE_TTL_MS = TimeUnit.HOURS.toMillis(6);

    private final ProxyServer proxy;
    private final Object taskOwner;
    private final Logger logger;
    private final Map<String, IspCacheEntry> cache = new ConcurrentHashMap<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private volatile boolean enabled;
    private volatile int timeoutMs;
    private volatile long limitReachedUntil;

    IspLookupService(ProxyServer proxy, Object taskOwner, Logger logger) {
        this.proxy = proxy;
        this.taskOwner = taskOwner;
        this.logger = logger;
    }

    void configure(boolean enabled, int timeoutMs) {
        this.enabled = enabled;
        this.timeoutMs = Math.max(100, timeoutMs);
    }

    String cached(String ip) {
        IspCacheEntry entry = cache.get(ip);
        if (entry == null) return null;
        if (entry.expiresAt() < System.currentTimeMillis()) {
            cache.remove(ip, entry);
            return null;
        }
        return entry.isp();
    }

    void cache(String ip, String isp) {
        if (ip != null && !ip.isBlank() && isp != null && !isp.isBlank()) {
            cache.put(ip, new IspCacheEntry(isp, System.currentTimeMillis() + CACHE_TTL_MS));
        }
    }

    void lookupAsync(String ip, Consumer<String> resultHandler) {
        if (ip == null || ip.isBlank() || !inFlight.add(ip)) return;
        proxy.getScheduler().buildTask(taskOwner, () -> {
            try {
                String result = lookup(ip);
                if (result != null && !result.isBlank()) {
                    cache(ip, result);
                    resultHandler.accept(result);
                }
            } finally {
                inFlight.remove(ip);
            }
        }).schedule();
    }

    String lookup(String ip) {
        if (!enabled || ip == null || ip.isBlank() || System.currentTimeMillis() < limitReachedUntil) return null;
        String url = ENDPOINT + URLEncoder.encode(ip, StandardCharsets.UTF_8) + "?fields=" + FIELDS;
        for (int attempt = 0; attempt < 2; attempt++) {
            HttpURLConnection connection = null;
            try {
                connection = HttpSupport.open(url);
                connection.setConnectTimeout(timeoutMs);
                connection.setReadTimeout(timeoutMs);
                connection.setRequestProperty("Accept", "application/json");
                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
                String body = HttpSupport.readText(stream, 12_000);
                if (status == 429) limitReachedUntil = System.currentTimeMillis() + LIMIT_BACKOFF_MS;
                if (status < 200 || status >= 300) return null;
                Boolean success = HttpSupport.jsonBoolean(body, "success");
                if (Boolean.FALSE.equals(success)) {
                    String message = HttpSupport.jsonString(body, "message");
                    if (message != null && message.toLowerCase(Locale.ROOT).contains("limit")) {
                        limitReachedUntil = System.currentTimeMillis() + LIMIT_BACKOFF_MS;
                    }
                    return null;
                }
                return HttpSupport.jsonString(body, "isp");
            } catch (Exception e) {
                if (HttpSupport.isRetryable(e) && attempt == 0) {
                    HttpSupport.sleep(150L);
                    continue;
                }
                logger.warn("ipwho.is lookup failed for {}: {}", ip, e.toString());
                return null;
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        return null;
    }
}
