package org.sagebionetworks.bridge.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;

public class CorrectAesGcmEncryptor implements Encryptor {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128; // Must be 128 bit auth tag
    private static final int IV_LENGTH_BYTE = 12; // 12 bytes IV is standard for GCM
    private final SecretKey secretKey;

    public CorrectAesGcmEncryptor(String secret) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] key = sha.digest(secret.getBytes(StandardCharsets.UTF_8));
            this.secretKey = new SecretKeySpec(key, "AES");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String encrypt(String text) {
        if (text == null)
            return null;
        try {
            byte[] iv = new byte[IV_LENGTH_BYTE];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] cipherText = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));

            // Return IV + CipherText (Base64 encoded)
            byte[] output = new byte[IV_LENGTH_BYTE + cipherText.length];
            System.arraycopy(iv, 0, output, 0, IV_LENGTH_BYTE);
            System.arraycopy(cipherText, 0, output, IV_LENGTH_BYTE, cipherText.length);
            return Base64.getEncoder().encodeToString(output);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    @Override
    public String decrypt(String text) {
        if (text == null)
            return null;
        try {
            byte[] decoded = Base64.getDecoder().decode(text);
            byte[] iv = new byte[IV_LENGTH_BYTE];
            System.arraycopy(decoded, 0, iv, 0, IV_LENGTH_BYTE);

            int cipherTextLen = decoded.length - IV_LENGTH_BYTE;
            byte[] cipherText = new byte[cipherTextLen];
            System.arraycopy(decoded, IV_LENGTH_BYTE, cipherText, 0, cipherTextLen);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
