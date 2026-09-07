package com.allergyout.global.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.allergyout.global.exception.CustomException;
import com.allergyout.global.exception.ErrorCode;

@Component
public class AesUtil {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_LENGTH = 32;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesUtil(@Value("${app.crypto.aes-key}") String base64Key) {
        byte[] keyBytes = decodeKey(base64Key, KEY_LENGTH, "app.crypto.aes-key");
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isBlank()) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return stored;
        }
        byte[] combined;
        try {
            combined = Base64.getDecoder().decode(stored);
        } catch (IllegalArgumentException e) {
            return stored; // 기존 평문
        }
        if (combined.length <= IV_LENGTH + 16) {
            return stored;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherText = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    static byte[] decodeKey(String base64Key, int expectedLength, String name) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(name + " 가 비어 있습니다.");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(name + " 는 Base64 여야 합니다.");
        }
        if (keyBytes.length != expectedLength) {
            throw new IllegalStateException(name + " 길이는 " + expectedLength + "바이트여야 합니다.");
        }
        return keyBytes;
    }
}