package io.github.katehok.finance.core;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Password {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 100_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] salt;
    private final byte[] hash;

    private Password(byte[] salt, byte[] hash) {
        this.salt = salt;
        this.hash = hash;
    }

    // Соль своя у каждого пользователя, поэтому одинаковые пароли дают разные хеши
    public static Password create(String raw) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return new Password(salt, hash(raw, salt));
    }

    public static Password of(byte[] salt, byte[] hash) {
        return new Password(salt.clone(), hash.clone());
    }

    // isEqual сравнивает за постоянное время, по времени ответа не угадать хеш
    public boolean matches(String raw) {
        return MessageDigest.isEqual(hash, hash(raw, salt));
    }

    public byte[] getSalt() {
        return salt.clone();
    }

    public byte[] getHash() {
        return hash.clone();
    }

    private static byte[] hash(String raw, byte[] salt) {
        char[] chars = raw.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, ITERATIONS, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(ALGORITHM + " is not available", e);
        } finally {
            spec.clearPassword();
            Arrays.fill(chars, '\0');
        }
    }
}
