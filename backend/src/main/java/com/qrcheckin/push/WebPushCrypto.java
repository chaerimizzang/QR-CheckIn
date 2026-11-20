package com.qrcheckin.push;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 웹 푸시 암호화(RFC 8291, aes128gcm)와 VAPID 서명(RFC 8292). 외부 라이브러리 없이 JDK 만 쓴다.
 * 알림 내용은 브라우저(구독)만 풀 수 있게 암호화되고, 푸시 서버(구글·애플)는 내용을 볼 수 없다.
 */
public final class WebPushCrypto {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final ECParameterSpec P256 = p256();

    private WebPushCrypto() {
    }

    // ---- 키 ----

    public static KeyPair newKeyPair() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
            return g.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 공개키 → 65바이트(0x04 || X || Y) */
    public static byte[] encodePublic(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 4;
        System.arraycopy(fixed32(key.getW().getAffineX()), 0, out, 1, 32);
        System.arraycopy(fixed32(key.getW().getAffineY()), 0, out, 33, 32);
        return out;
    }

    /** 개인키 → 32바이트(d) */
    public static byte[] encodePrivate(ECPrivateKey key) {
        return fixed32(key.getS());
    }

    public static ECPublicKey decodePublic(byte[] raw) {
        if (raw.length != 65 || raw[0] != 4) {
            throw new IllegalArgumentException("P-256 공개키 형식이 아니에요");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(raw, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(raw, 33, 65));
        try {
            return (ECPublicKey) KeyFactory.getInstance("EC")
                    .generatePublic(new ECPublicKeySpec(new ECPoint(x, y), P256));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("P-256 공개키 형식이 아니에요", e);
        }
    }

    public static ECPrivateKey decodePrivate(byte[] raw) {
        try {
            return (ECPrivateKey) KeyFactory.getInstance("EC")
                    .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, raw), P256));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("P-256 개인키 형식이 아니에요", e);
        }
    }

    public static String b64(byte[] bytes) {
        return B64.encodeToString(bytes);
    }

    public static byte[] unb64(String s) {
        // 브라우저마다 패딩·표준 Base64 로 줄 수 있어 모두 받는다
        return B64D.decode(s.trim().replace('+', '-').replace('/', '_').replace("=", ""));
    }

    // ---- 암호화 (RFC 8291) ----

    /**
     * @param uaPublic 구독의 p256dh(65바이트)
     * @param authSecret 구독의 auth(16바이트)
     * @return 요청 본문(헤더 + 암호문). Content-Encoding: aes128gcm
     */
    public static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret) {
        return encrypt(plaintext, uaPublic, authSecret, newKeyPair(), randomBytes(16));
    }

    static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret, KeyPair ephemeral, byte[] salt) {
        try {
            byte[] asPublic = encodePublic((ECPublicKey) ephemeral.getPublic());
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(ephemeral.getPrivate());
            ka.doPhase(decodePublic(uaPublic), true);
            byte[] shared = ka.generateSecret();

            byte[] prkKey = hmac(authSecret, shared);
            byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
            byte[] ikm = hmac(prkKey, concat(keyInfo, new byte[] {1}));

            byte[] prk = hmac(salt, ikm);
            byte[] cek = Arrays.copyOf(hmac(prk, concat("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII),
                    new byte[] {1})), 16);
            byte[] nonce = Arrays.copyOf(hmac(prk, concat("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII),
                    new byte[] {1})), 12);

            Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
            gcm.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            byte[] cipher = gcm.doFinal(concat(plaintext, new byte[] {2})); // 0x02 = 마지막 레코드

            ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length);
            header.put(salt).putInt(4096).put((byte) asPublic.length).put(asPublic);
            return concat(header.array(), cipher);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("웹 푸시 암호화 실패", e);
        }
    }

    // ---- VAPID (RFC 8292) ----

    /** Authorization 헤더 값: "vapid t=<JWT>, k=<공개키>" */
    public static String vapidAuthorization(String audience, String subject, long expiresEpochSeconds,
                                            byte[] vapidPublic, PrivateKey vapidPrivate) {
        String header = b64("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = b64(("{\"aud\":\"" + audience + "\",\"exp\":" + expiresEpochSeconds
                + ",\"sub\":\"" + subject + "\"}").getBytes(StandardCharsets.UTF_8));
        String unsigned = header + "." + claims;
        try {
            Signature sig = Signature.getInstance("SHA256withECDSAinP1363Format"); // R||S 64바이트(JWT 형식)
            sig.initSign(vapidPrivate);
            sig.update(unsigned.getBytes(StandardCharsets.US_ASCII));
            return "vapid t=" + unsigned + "." + b64(sig.sign()) + ", k=" + b64(vapidPublic);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("VAPID 서명 실패", e);
        }
    }

    // ---- 도우미 ----

    static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.writeBytes(p);
        }
        return out.toByteArray();
    }

    static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    private static byte[] fixed32(BigInteger v) {
        byte[] raw = v.toByteArray();
        byte[] out = new byte[32];
        int copy = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - copy, out, 32 - copy, copy);
        return out;
    }

    private static ECParameterSpec p256() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"));
            return ((ECPublicKey) g.generateKeyPair().getPublic()).getParams();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
