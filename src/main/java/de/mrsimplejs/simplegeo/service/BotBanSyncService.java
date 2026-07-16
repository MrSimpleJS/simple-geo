package de.mrsimplejs.simplegeo;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class BotBanSyncService implements AutoCloseable {
    private static final long NETWORK_BACKOFF_MS = TimeUnit.MINUTES.toMillis(10);
    private final ProxyServer proxy;
    private final Object taskOwner;
    private final Logger logger;
    private final Path botBanFile;
    private final BotBanRepository repository;
    private ScheduledTask uploadTask;
    private ScheduledTask unbanTask;
    private String uploadUrl = "", uploadSecret = "", unbanUrl = "", unbanSecret = "";
    private long lastModified, lastSize = -1, uploadBackoffUntil, unbanBackoffUntil;

    BotBanSyncService(ProxyServer proxy, Object taskOwner, Logger logger, Path botBanFile,
                      BotBanRepository repository) {
        this.proxy = proxy; this.taskOwner = taskOwner; this.logger = logger;
        this.botBanFile = botBanFile; this.repository = repository;
    }

    void start(PluginSettings settings) {
        close();
        uploadUrl = settings.botbanSyncUrl(); uploadSecret = settings.botbanSyncSecret();
        unbanUrl = settings.botbanUnbanSyncUrl(); unbanSecret = settings.botbanUnbanSyncSecret();
        if (!uploadUrl.isBlank() && !uploadSecret.isBlank() && settings.botbanSyncIntervalSeconds() > 0) {
            uploadTask = proxy.getScheduler().buildTask(taskOwner, this::uploadIfChanged)
                .repeat(settings.botbanSyncIntervalSeconds(), TimeUnit.SECONDS).schedule();
        }
        if (!unbanUrl.isBlank() && !unbanSecret.isBlank() && settings.botbanUnbanSyncIntervalSeconds() > 0) {
            unbanTask = proxy.getScheduler().buildTask(taskOwner, this::fetchUnbans)
                .repeat(settings.botbanUnbanSyncIntervalSeconds(), TimeUnit.SECONDS).schedule();
        }
    }

    private void uploadIfChanged() {
        if (System.currentTimeMillis() < uploadBackoffUntil || !Files.isRegularFile(botBanFile)) return;
        try {
            long modified = Files.getLastModifiedTime(botBanFile).toMillis(), size = Files.size(botBanFile);
            if (modified == lastModified && size == lastSize) return;
            if (post(uploadUrl, uploadSecret, "text/plain", Files.readAllBytes(botBanFile), false) != null) {
                lastModified = modified; lastSize = size;
            }
        } catch (IOException e) { logger.warn("Botban sync failed.", e); }
    }

    private void fetchUnbans() {
        if (System.currentTimeMillis() < unbanBackoffUntil) return;
        byte[] response = post(unbanUrl, unbanSecret, "application/json", "{}".getBytes(StandardCharsets.UTF_8), true);
        if (response == null) return;
        Matcher matcher = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(new String(response, StandardCharsets.UTF_8));
        while (matcher.find()) {
            try { repository.remove(Integer.parseInt(matcher.group(1))); }
            catch (NumberFormatException ignored) { }
        }
    }

    private byte[] post(String initialUrl, String secret, String contentType, byte[] payload, boolean unban) {
        String url = initialUrl;
        for (int redirect = 0; redirect < 2; redirect++) {
            HttpURLConnection connection = null;
            try {
                connection = HttpSupport.open(url);
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(5000); connection.setReadTimeout(5000);
                connection.setRequestMethod("POST"); connection.setDoOutput(true);
                connection.setRequestProperty("Authorization", "Bearer " + secret);
                connection.setRequestProperty("Content-Type", contentType);
                try (OutputStream output = connection.getOutputStream()) { output.write(payload); }
                int status = connection.getResponseCode();
                if (status >= 200 && status < 300) {
                    try (InputStream input = connection.getInputStream()) { return HttpSupport.readBytes(input); }
                }
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String next = safeRedirect(url, connection.getHeaderField("Location"));
                    if (next != null) { url = next; continue; }
                }
                logger.warn("Botban {} sync failed: HTTP {}.", unban ? "unban" : "upload", status);
                return null;
            } catch (UnknownHostException e) {
                setBackoff(unban); logger.warn("Botban sync host could not be resolved: {}.", host(url)); return null;
            } catch (IOException e) {
                setBackoff(unban); logger.warn("Botban sync I/O failure for {}.", host(url), e); return null;
            } finally { if (connection != null) connection.disconnect(); }
        }
        return null;
    }

    private void setBackoff(boolean unban) {
        if (unban) unbanBackoffUntil = System.currentTimeMillis() + NETWORK_BACKOFF_MS;
        else uploadBackoffUntil = System.currentTimeMillis() + NETWORK_BACKOFF_MS;
    }

    private String safeRedirect(String currentUrl, String location) {
        if (location == null || location.isBlank()) return null;
        try {
            URI current = URI.create(currentUrl), next = current.resolve(location);
            return "https".equalsIgnoreCase(next.getScheme()) && current.getHost() != null
                && current.getHost().equalsIgnoreCase(next.getHost()) ? next.toString() : null;
        } catch (IllegalArgumentException e) { return null; }
    }

    private static String host(String url) {
        try { String host = URI.create(url).getHost(); return host == null ? url : host; }
        catch (IllegalArgumentException e) { return url; }
    }

    @Override public void close() {
        if (uploadTask != null) uploadTask.cancel();
        if (unbanTask != null) unbanTask.cancel();
        uploadTask = null; unbanTask = null;
    }
}
