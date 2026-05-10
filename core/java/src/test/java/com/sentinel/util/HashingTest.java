package com.lumebridge.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class HashingTest {

    @Test
    void sha256HexBytesMatchesKnownVector() {
        assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                Hashing.sha256Hex(new byte[0]));
    }

    @Test
    void sha256HexStringMatchesBytes() {
        String s = "sentinel";
        assertEquals(Hashing.sha256Hex(s.getBytes()), Hashing.sha256Hex(s));
    }

    @Test
    void differentInputDifferentDigest() {
        assertNotEquals(Hashing.sha256Hex("a"), Hashing.sha256Hex("b"));
    }
}
