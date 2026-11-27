package io.github.katehok.finance.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PasswordTest {

    @Test
    void matchesSamePassword() {
        assertTrue(Password.create("secret1").matches("secret1"));
    }

    @Test
    void rejectsWrongPassword() {
        Password password = Password.create("secret1");
        assertFalse(password.matches("secret2"));
        assertFalse(password.matches("Secret1"));
        assertFalse(password.matches(""));
    }

    @Test
    void samePasswordGetsDifferentSalts() {
        Password first = Password.create("secret1");
        Password second = Password.create("secret1");
        assertFalse(Arrays.equals(first.getSalt(), second.getSalt()));
        assertFalse(Arrays.equals(first.getHash(), second.getHash()));
    }

    @Test
    void ofRestoresPassword() {
        Password password = Password.create("secret1");
        Password restored = Password.of(password.getSalt(), password.getHash());
        assertTrue(restored.matches("secret1"));
        assertFalse(restored.matches("secret2"));
    }

    @Test
    void ofCopiesArrays() {
        Password password = Password.create("secret1");
        byte[] salt = password.getSalt();
        byte[] hash = password.getHash();
        Password restored = Password.of(salt, hash);
        Arrays.fill(salt, (byte) 0);
        Arrays.fill(hash, (byte) 0);
        assertTrue(restored.matches("secret1"));
    }

    @Test
    void gettersReturnCopies() {
        Password password = Password.create("secret1");
        byte[] salt = password.getSalt();
        Arrays.fill(salt, (byte) 0);
        assertFalse(Arrays.equals(salt, password.getSalt()));
        byte[] hash = password.getHash();
        Arrays.fill(hash, (byte) 0);
        assertTrue(password.matches("secret1"));
        assertArrayEquals(password.getHash(), password.getHash());
    }
}
