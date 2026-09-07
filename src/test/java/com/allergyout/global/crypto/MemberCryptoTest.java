package com.allergyout.global.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemberCryptoTest {

    private AesUtil aesUtil;
    private HmacUtil hmacUtil;

    @BeforeEach
    void setUp() {
        String aes = Base64.getEncoder().encodeToString(new byte[32]);
        String hmac = Base64.getEncoder().encodeToString(Arrays.copyOf(new byte[] {1}, 32));
        aesUtil = new AesUtil(aes);
        hmacUtil = new HmacUtil(hmac);
    }

    @Test
    void aesRoundTrip() {
        String a = aesUtil.encrypt("min@test.com");
        String b = aesUtil.encrypt("min@test.com");
        assertThat(a).isNotEqualTo(b);
        assertThat(aesUtil.decrypt(a)).isEqualTo("min@test.com");
    }

    @Test
    void hmacStable() {
        assertThat(hmacUtil.hash("min@test.com"))
                .isEqualTo(hmacUtil.hash("min@test.com"))
                .hasSize(64);
    }
}