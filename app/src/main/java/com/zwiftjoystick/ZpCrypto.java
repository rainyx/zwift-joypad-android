package com.zwiftjoystick;

import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.modes.CCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.crypto.params.KeyParameter;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;

import javax.crypto.KeyAgreement;

/**
 * 加密设备使用的会话加密：
 * P-256 ECDH → HKDF-SHA256(salt = devicePub64 || appPub64) → 36 字节
 * key = out[0:32], ivPrefix = out[32:36]
 * AES-256-CCM, nonce(8) = ivPrefix || counter(LE32), tag 4 字节
 * 帧 = counter(LE32) || ciphertext || tag
 */
final class ZpCrypto {
    private final KeyPair keyPair;
    private byte[] key;
    private byte[] ivPrefix;
    private int txCounter;

    ZpCrypto() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = g.generateKeyPair();
    }

    /** 本机（设备侧）公钥 X||Y，64 字节。 */
    byte[] publicKey64() {
        ECPoint w = ((ECPublicKey) keyPair.getPublic()).getW();
        byte[] out = new byte[64];
        put32(out, 0, w.getAffineX());
        put32(out, 32, w.getAffineY());
        return out;
    }

    /** appPub64 = Zwift 在 RideOn 里发来的 64 字节公钥。 */
    void derive(byte[] appPub64) throws Exception {
        ECPublicKey mine = (ECPublicKey) keyPair.getPublic();
        BigInteger x = new BigInteger(1, java.util.Arrays.copyOfRange(appPub64, 0, 32));
        BigInteger y = new BigInteger(1, java.util.Arrays.copyOfRange(appPub64, 32, 64));
        PublicKey peer = KeyFactory.getInstance("EC")
                .generatePublic(new ECPublicKeySpec(new ECPoint(x, y), mine.getParams()));
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(keyPair.getPrivate());
        ka.doPhase(peer, true);
        byte[] secret = ka.generateSecret();

        byte[] salt = new byte[128];
        System.arraycopy(publicKey64(), 0, salt, 0, 64);   // 设备公钥在前
        System.arraycopy(appPub64, 0, salt, 64, 64);
        HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
        hkdf.init(new HKDFParameters(secret, salt, null));
        byte[] okm = new byte[36];
        hkdf.generateBytes(okm, 0, okm.length);
        key = java.util.Arrays.copyOfRange(okm, 0, 32);
        ivPrefix = java.util.Arrays.copyOfRange(okm, 32, 36);
        txCounter = 0;
    }

    boolean ready() {
        return key != null;
    }

    byte[] encrypt(byte[] plain) throws Exception {
        int counter = txCounter++;
        byte[] out = ccm(true, counter, plain, 0, plain.length);
        byte[] frame = new byte[4 + out.length];
        le32(frame, 0, counter);
        System.arraycopy(out, 0, frame, 4, out.length);
        return frame;
    }

    byte[] decrypt(byte[] frame) throws Exception {
        if (frame.length < 8) throw new IllegalArgumentException("message too small");
        int counter = (frame[0] & 0xFF) | (frame[1] & 0xFF) << 8 | (frame[2] & 0xFF) << 16 | (frame[3] & 0xFF) << 24;
        return ccm(false, counter, frame, 4, frame.length - 4);
    }

    private byte[] ccm(boolean enc, int counter, byte[] in, int off, int len) throws Exception {
        byte[] nonce = new byte[8];
        System.arraycopy(ivPrefix, 0, nonce, 0, 4);
        le32(nonce, 4, counter);
        CCMBlockCipher c = new CCMBlockCipher(AESEngine.newInstance());
        c.init(enc, new AEADParameters(new KeyParameter(key), 32, nonce));
        byte[] out = new byte[c.getOutputSize(len)];
        int n = c.processBytes(in, off, len, out, 0);
        n += c.doFinal(out, n);
        return n == out.length ? out : java.util.Arrays.copyOf(out, n);
    }

    private static void le32(byte[] b, int off, int v) {
        b[off] = (byte) v;
        b[off + 1] = (byte) (v >> 8);
        b[off + 2] = (byte) (v >> 16);
        b[off + 3] = (byte) (v >> 24);
    }

    private static void put32(byte[] dst, int off, BigInteger v) {
        byte[] b = v.toByteArray();
        int n = Math.min(b.length, 32);
        System.arraycopy(b, b.length - n, dst, off + 32 - n, n);
    }
}
