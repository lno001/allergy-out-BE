package com.allergyout.global.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.allergyout.global.exception.CustomException;
import com.allergyout.global.exception.ErrorCode;

@Component
public class HmacUtil {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int KEY_LENGTH = 32;

    private final SecretKey secretKey;

    public HmacUtil(@Value("${app.crypto.hmac-key}") String base64Key) {
        byte[] keyBytes = AesUtil.decodeKey(base64Key, KEY_LENGTH, "app.crypto.hmac-key");
        this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    public String hash(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(secretKey);
            byte[] digest = mac.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}