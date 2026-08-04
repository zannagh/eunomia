package de.zannagh.eunomia.networking.serialization;

import org.jspecify.annotations.NonNull;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Fails fast once more than {@code limit} bytes have been read, so an over-large gzip stream is
 * rejected during inflation rather than after it has already been materialised in memory. This is
 * the real defence against a decompression bomb on an attacker-controlled payload - bounding the
 * compressed length alone is not enough, since gzip routinely hits high ratios on this JSON.
 */
final class SizeLimitedInputStream extends FilterInputStream {

    private final long limit;
    private long consumed;

    SizeLimitedInputStream(InputStream delegate, long limit) {
        super(delegate);
        this.limit = limit;
    }

    private void recordRead(long readCount) throws IOException {
        if (readCount <= 0) {
            return;
        }
        consumed += readCount;
        if (consumed > limit) {
            throw new IOException("Rejecting a payload that inflates beyond " + limit
                    + " bytes - refusing to decompress further");
        }
    }

    @Override
    public int read() throws IOException {
        int value = super.read();
        if (value != -1) {
            recordRead(1);
        }
        return value;
    }

    @Override
    public int read(byte @NonNull [] buffer, int off, int len) throws IOException {
        int readCount = super.read(buffer, off, len);
        recordRead(readCount);
        return readCount;
    }

    @Override
    public long skip(long n) throws IOException {
        long skipped = super.skip(n);
        recordRead(skipped);
        return skipped;
    }
}
