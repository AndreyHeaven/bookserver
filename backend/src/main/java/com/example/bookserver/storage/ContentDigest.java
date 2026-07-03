package com.example.bookserver.storage;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * MD5 hash and byte size of a stream's content, computed by consuming the stream
 * without persisting it. Used to describe individual files that live inside an
 * archive (which is stored as-is) so books can still be deduplicated by content.
 */
public record ContentDigest(String md5, long size) {

    private static final int BUFFER_SIZE = 8192;

    public static ContentDigest of(InputStream input) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            long size = 0;
            byte[] buffer = new byte[BUFFER_SIZE];
            try (DigestInputStream in = new DigestInputStream(input, digest)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    size += read;
                }
            }
            return new ContentDigest(HexFormat.of().formatHex(digest.digest()), size);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 digest is not available", e);
        }
    }
}
