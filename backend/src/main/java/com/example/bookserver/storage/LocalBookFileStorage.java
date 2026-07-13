package com.example.bookserver.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Component
public class LocalBookFileStorage implements BookFileStorage {

    private final Path baseDir;
    /** Trusted root that in-place references must stay within (same dir the importer reads from). */
    private final Path importsBaseDir;
    private final StorageMode storageMode;

    public LocalBookFileStorage(@Value("${app.storage.books-dir:./data/books}") String baseDir,
                                @Value("${app.imports.base-dir:./data/imports}") String importsBaseDir,
                                @Value("${app.imports.storage-mode:copy}") String storageMode) {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
        this.importsBaseDir = Path.of(importsBaseDir).toAbsolutePath().normalize();
        this.storageMode = StorageMode.from(storageMode);
    }

    @Override
    public StoredFile store(InputStream input, String fileName) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            Path temp = Files.createTempFile("book-import-", ".tmp");
            long size;
            try (DigestInputStream in = new DigestInputStream(input, digest)) {
                size = Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            String md5 = HexFormat.of().formatHex(digest.digest());
            String extension = extension(fileName);
            String shard = md5.length() >= 2 ? md5.substring(0, 2) : UUID.randomUUID().toString().substring(0, 2);
            Path target = baseDir.resolve(shard).resolve(md5 + extension).normalize();
            if (!target.startsWith(baseDir)) {
                throw new IOException("Resolved storage path escapes books dir");
            }
            Files.createDirectories(target.getParent());
            Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return new StoredFile(baseDir.relativize(target).toString(), size, md5);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 digest is not available", e);
        }
    }

    @Override
    public StoredFile store(Path source) throws IOException {
        if (storageMode == StorageMode.IN_PLACE) {
            return reference(source);
        }
        try (InputStream input = Files.newInputStream(source)) {
            return store(input, source.getFileName().toString());
        }
    }

    /**
     * Records an existing file without copying it: computes its content hash/size and returns a
     * {@link StoredFile} whose path is the absolute location of the original. The source must live
     * inside {@link #importsBaseDir} so later downloads can safely resolve it.
     */
    private StoredFile reference(Path source) throws IOException {
        Path normalized = source.toAbsolutePath().normalize();
        if (!normalized.startsWith(importsBaseDir)) {
            throw new IOException("In-place source escapes imports dir: " + normalized);
        }
        ContentDigest digest;
        try (InputStream input = Files.newInputStream(normalized)) {
            digest = ContentDigest.of(input);
        }
        return new StoredFile(normalized.toString(), digest.size(), digest.md5());
    }

    @Override
    public InputStream open(String path) throws IOException {
        return Files.newInputStream(resolve(path));
    }

    @Override
    public InputStream openEntry(String archivePath, String entryName) throws IOException {
        ZipFile zip = new ZipFile(resolve(archivePath).toFile());
        ZipEntry entry = zip.getEntry(entryName);
        if (entry == null) {
            zip.close();
            throw new IOException("Entry " + entryName + " not found in archive " + archivePath);
        }
        // Wrap so that closing the entry stream also closes the backing archive.
        return new FilterInputStream(zip.getInputStream(entry)) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    zip.close();
                }
            }
        };
    }

    @Override
    public void delete(String path) throws IOException {
        Files.deleteIfExists(resolve(path));
    }

    private Path resolve(String path) throws IOException {
        Path candidate = Path.of(path);
        // In-place references are stored as absolute paths inside the trusted imports dir.
        if (candidate.isAbsolute()) {
            Path normalized = candidate.normalize();
            if (!normalized.startsWith(importsBaseDir)) {
                throw new IOException("Resolved storage path escapes imports dir");
            }
            return normalized;
        }
        Path resolved = baseDir.resolve(path).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IOException("Resolved storage path escapes books dir");
        }
        return resolved;
    }

    private static String extension(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]", "");
    }
}
