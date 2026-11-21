package ua.empirecraft.forgemigration;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

public final class LegacyPassword {
    public static final String PREFIX = "rlogin:";

    private LegacyPassword() {}

    public static boolean verify(char[] password, String salt, String hash, int iterations) {
        if (iterations != 1 || password == null || salt == null || !salt.startsWith(PREFIX) || hash == null) return false;
        ByteBuffer bytes = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
        try {
            byte[] expected = Base64.getDecoder().decode(hash);
            String originalSalt = salt.substring(PREFIX.length());
            if (expected.length != 32 || Base64.getDecoder().decode(originalSalt).length != 16) return false;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(originalSalt.getBytes(StandardCharsets.UTF_8));
            digest.update(bytes);
            byte[] actual = digest.digest();
            try {
                return MessageDigest.isEqual(expected, actual);
            } finally {
                Arrays.fill(actual, (byte) 0);
            }
        } catch (IllegalArgumentException malformed) {
            return false;
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        } finally {
            Arrays.fill(bytes.array(), (byte) 0);
        }
    }
}
