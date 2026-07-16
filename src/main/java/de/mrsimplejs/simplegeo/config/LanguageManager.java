package de.mrsimplejs.simplegeo;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class LanguageManager {
    private static final String DEFAULT_LANGUAGE = "en";
    private static final String LANG_DIRECTORY = "lang";

    private final Path dataDirectory;
    private final Logger logger;
    private final Properties messages = new Properties();

    LanguageManager(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    String load(String requestedLanguage) {
        Path langDirectory = dataDirectory.resolve(LANG_DIRECTORY);
        copyDefault(langDirectory, "en");
        copyDefault(langDirectory, "de");

        String language = requestedLanguage == null || requestedLanguage.isBlank()
            ? DEFAULT_LANGUAGE
            : requestedLanguage.trim().toLowerCase(java.util.Locale.ROOT);
        Path selected = langDirectory.resolve(language + ".properties");
        if (!Files.isRegularFile(selected)) {
            logger.warn("Language '{}' was not found. Falling back to English.", language);
            language = DEFAULT_LANGUAGE;
            selected = langDirectory.resolve(DEFAULT_LANGUAGE + ".properties");
        }
        messages.clear();
        try (java.io.Reader reader = Files.newBufferedReader(selected, StandardCharsets.UTF_8)) {
            messages.load(reader);
        } catch (IOException e) {
            logger.warn("Could not load language file {}.", selected, e);
        }
        return language;
    }

    String message(String key, String... replacements) {
        String message = messages.getProperty(key, key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return message;
    }

    private void copyDefault(Path langDirectory, String code) {
        Path destination = langDirectory.resolve(code + ".properties");
        if (Files.exists(destination)) {
            return;
        }
        try {
            Files.createDirectories(langDirectory);
            try (InputStream input = SimpleGeoPlugin.class.getResourceAsStream("/lang/" + code + ".properties")) {
                if (input == null) {
                    logger.warn("Bundled language '{}' is missing.", code);
                    return;
                }
                Files.copy(input, destination);
            }
        } catch (IOException e) {
            logger.warn("Could not create language file {}.", destination, e);
        }
    }
}
