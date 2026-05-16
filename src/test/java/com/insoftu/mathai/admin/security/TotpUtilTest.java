package com.insoftu.mathai.admin.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TotpUtilTest {

    private final TotpUtil totpUtil = new TotpUtil();

    @Test
    void shouldGenerateValidSecret() {
        String secret = totpUtil.generateSecret();
        assertNotNull(secret);
        assertTrue(secret.length() >= 16, "Secret should be at least 16 chars");
    }

    @Test
    void shouldGenerateUniqueSecrets() {
        String secret1 = totpUtil.generateSecret();
        String secret2 = totpUtil.generateSecret();
        assertNotEquals(secret1, secret2);
    }

    @Test
    void shouldGenerateQrDataUri() {
        String uri = totpUtil.generateQrDataUri("admin@example.com", "TESTSECRETKEY123");
        assertNotNull(uri);
        assertTrue(uri.startsWith("data:image/png;base64,"), "Should be a base64 data URI");
    }

    @Test
    void shouldRejectWrongCode() {
        String secret = totpUtil.generateSecret();
        assertFalse(totpUtil.verifyCode("000000", secret));
    }

    @Test
    void shouldRejectInvalidCodeFormat() {
        String secret = totpUtil.generateSecret();
        assertFalse(totpUtil.verifyCode("abc", secret));
        assertFalse(totpUtil.verifyCode("", secret));
        assertFalse(totpUtil.verifyCode(null, secret));
    }
}
