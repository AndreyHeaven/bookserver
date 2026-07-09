package com.example.bookserver.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class LocalCoverStorage implements CoverStorage {

    private final Path baseDir;

    public LocalCoverStorage(@Value("${app.storage.covers-dir:./data/covers}") String baseDir) {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
    }

    @Override
    public String store(byte[] data, String contentType) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            String md5 = HexFormat.of().formatHex(digest.digest(data));
            String extension = extension(contentType);
            String shard = md5.substring(0, 2);
            Path target = baseDir.resolve(shard).resolve(md5 + "." + extension).normalize();
            if (!target.startsWith(baseDir)) {
                throw new IOException("Resolved storage path escapes covers dir");
            }
            Files.createDirectories(target.getParent());
            if (!Files.exists(target)) {
                Files.write(target, data);
            }
            return baseDir.relativize(target).toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 digest is not available", e);
        }
    }

    @Override
    public InputStream open(String path) throws IOException {
        Path resolved = baseDir.resolve(path).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IOException("Resolved storage path escapes covers dir");
        }
        if (!Files.exists(resolved)) {
            // Fall back to an empty stream guard; callers treat IOException as failure.
            throw new IOException("Cover not found: " + path);
        }
        return new ByteArrayInputStream(Files.readAllBytes(resolved));
    }

    private static String extension(String contentType) {
        String type = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            default -> "bin";
        };
    }
}
