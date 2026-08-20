package de.zannagh.eunomia.configuration;

import com.google.gson.Gson;
import org.slf4j.Logger;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A generic, JSON-file-backed {@link ConfigurationProvider}. It loads a single {@link ConfigurationItem} from a
 * file on construction (writing defaults if the file is missing or unreadable), applies any pending schema
 * migration via {@link ConfigurationItem#ensureSchemaFrom}, and persists on demand. Deliberately game-agnostic -
 * the concrete type, its default factory, the file path and the {@link Gson} are all injected, so a consumer mod
 * (or eunomia's own client config) reuses it instead of hand-rolling load/save/migrate.
 *
 * @param <T> the configuration type managed
 * @since 0.1.0
 */
public class FileConfigurationProvider<T extends ConfigurationItem<T>> implements ConfigurationProvider<T> {

    private final Path file;

    private final Class<T> type;

    private final Supplier<T> defaultFactory;

    private final Gson gson;

    private final Logger logger;

    private T current;

    public FileConfigurationProvider(Path file, Class<T> type, Supplier<T> defaultFactory, Gson gson, Logger logger) {
        this.file = Objects.requireNonNull(file, "file");
        this.type = Objects.requireNonNull(type, "type");
        this.defaultFactory = Objects.requireNonNull(defaultFactory, "defaultFactory");
        this.gson = Objects.requireNonNull(gson, "gson");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.current = load();
    }

    @Override
    public T getValue() {
        return current;
    }

    @Override
    public void update(T newValue) {
        current = newValue;
    }

    @Override
    public T load() {
        try {
            if (!Files.exists(file)) {
                T defaults = getDefault();
                current = defaults;
                saveCurrent();
                return defaults;
            }
            try (Reader reader = Files.newBufferedReader(file)) {
                T loaded = gson.fromJson(reader, type);
                if (loaded == null) {
                    throw new IllegalStateException("Config file " + file + " was empty or deserialized to null");
                }
                T migrated = loaded.ensureSchemaFrom(loaded);
                current = migrated;
                if (migrated.hasChangedFromSerializedContent()) {
                    saveCurrent();
                }
                return migrated;
            }
        } catch (Exception e) {
            logger.error("Failed to load config from {}, replacing with defaults.", file, e);
            T defaults = getDefault();
            current = defaults;
            saveCurrent();
            return defaults;
        }
    }

    @Override
    public void saveCurrent() {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(file)) {
                gson.toJson(current, writer);
            }
        } catch (Exception e) {
            logger.error("Failed to save config to {}.", file, e);
        }
    }

    @Override
    public T getDefault() {
        T defaults = defaultFactory.get();
        if (defaults == null) {
            throw new IllegalStateException("Config default factory returned null");
        }
        return defaults;
    }
}
