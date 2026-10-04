package io.civicpulse.shared;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.UUID;

// UUID v7
public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {
    }

    public static UUID newId() {
        return newId(System.currentTimeMillis());
    }

    static UUID newId(long epochMillis) {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        for (int i = 0; i < 6; i++) {
            bytes[i] = (byte) (epochMillis >>> (40 - 8 * i));
        }
        bytes[6] = (byte) ((bytes[6] & 0x0f) | 0x70);
        bytes[8] = (byte) ((bytes[8] & 0x3f) | 0x80);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
