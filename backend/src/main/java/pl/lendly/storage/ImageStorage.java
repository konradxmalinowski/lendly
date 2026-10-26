package pl.lendly.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import pl.lendly.config.StorageProperties;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;

/** Stores image bytes on the local filesystem under generated keys; no caller-supplied name reaches a path. */
@Component
public class ImageStorage {
    private static final Logger log = LoggerFactory.getLogger(ImageStorage.class);
    private static final Pattern KEY = Pattern.compile("[0-9a-f]{32}");
    private static final String LOCAL_DEFAULT_DIRECTORY = "var/images";
    private final Path root;

    public ImageStorage(StorageProperties properties, Environment environment) throws IOException {
        String configured = properties.getImageDir();
        if (configured.isBlank()) {
            if (!environment.matchesProfiles("local")) {
                throw new IllegalStateException("LENDLY_IMAGE_DIR must be configured outside the local profile.");
            }
            configured = LOCAL_DEFAULT_DIRECTORY;
        }
        this.root = Path.of(configured).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public String store(byte[] bytes) {
        String key = UUID.randomUUID().toString().replace("-", "");
        Path target = resolve(key);
        Path temporary = null;
        try {
            temporary = Files.createTempFile(root, ".upload-", ".tmp");
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target);
            }
            return key;
        } catch (IOException exception) {
            deleteQuietly(temporary);
            throw new IllegalStateException("Image could not be stored.", exception);
        }
    }

    public byte[] read(String key) throws IOException {
        return Files.readAllBytes(resolve(key));
    }

    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException exception) {
            log.warn("Image file could not be deleted, storageKey={}", key);
        }
    }

    private Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) throw new IllegalArgumentException("Invalid storage key.");
        Path path = root.resolve(key + ".jpg").normalize();
        if (!path.getParent().equals(root)) throw new IllegalArgumentException("Invalid storage path.");
        return path;
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }
}
