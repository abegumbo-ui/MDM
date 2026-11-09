package com.familymdm.agent.inspect;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509ExtendedKeyManager;

/**
 * This phone's own certificate authority, used only by the inspection proxy (see InspectProxy).
 *
 * The CA's private key never leaves wherever the {@link Signer} keeps it. On the phone that is the
 * Android Keystore, which can sign but cannot hand the key out. Each phone makes its own CA, so a
 * certificate from one phone is useless on any other.
 *
 * For every site the proxy needs to look inside, this makes a short-lived certificate for exactly
 * that site name, signed by the CA. The certificates share one throw-away key that exists only in
 * memory.
 *
 * Pure Java, so it is checked on a computer. See InspectCheck.
 */
public final class CertAuthority {
    /** Signs bytes with the CA key as ECDSA over SHA-256, returning the signature in DER form. */
    public interface Signer {
        byte[] sign(byte[] data) throws Exception;
    }

    private static final String OID_CN = "2.5.4.3";
    private static final String OID_O = "2.5.4.10";
    private static final String OID_ECDSA_SHA256 = "1.2.840.10045.4.3.2";
    private static final long LEAF_VALID_MS = 30L * 24 * 3600 * 1000;
    private static final long CA_VALID_MS = 10L * 365 * 24 * 3600 * 1000;
    private static final long BACKDATE_MS = 24L * 3600 * 1000; // tolerate a phone clock that is a little behind

    private final Signer signer;
    private final X509Certificate caCert;
    private final byte[] caSubjectDn;
    private final byte[] caKeyId;
    private final KeyPair leafKeys;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> cache = new HashMap<>();

    private static final class Entry {
        final SSLContext ctx;
        final long expires;

        Entry(SSLContext ctx, long expires) {
            this.ctx = ctx;
            this.expires = expires;
        }
    }

    /** Uses an existing CA certificate (for example one loaded back from storage). */
    public CertAuthority(Signer signer, byte[] caCertDer) throws Exception {
        this.signer = signer;
        this.caCert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(caCertDer));
        this.caSubjectDn = caCert.getSubjectX500Principal().getEncoded();
        this.caKeyId = keyId(caCert.getPublicKey());
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        this.leafKeys = g.generateKeyPair();
    }

    /** Makes a brand-new CA certificate for `caPublic` (whose private half `signer` holds). */
    public static byte[] newCaCertificate(Signer signer, PublicKey caPublic, String commonName, long now) throws Exception {
        byte[] dn = Der.name(OID_CN, commonName, OID_O, "MDM");
        byte[] id = keyId(caPublic);
        byte[] exts = Der.sequence(
                // This certificate may sign others, but only ones for sites (path length 0: no sub-CAs).
                Der.extension("2.5.29.19", true, Der.sequence(Der.bool(true), Der.integer(0))),
                Der.extension("2.5.29.15", true, Der.bitString(new byte[] {0x06}, 1)), // keyCertSign + cRLSign
                Der.extension("2.5.29.14", false, Der.octetString(id)));
        return certificate(signer, new BigInteger(120, new SecureRandom()).add(BigInteger.ONE), dn, dn, caPublic, now - BACKDATE_MS, now + CA_VALID_MS, exts);
    }

    private static byte[] certificate(Signer signer, BigInteger serial, byte[] issuerDn, byte[] subjectDn, PublicKey subjectKey,
                                      long notBefore, long notAfter, byte[] extensions) throws Exception {
        byte[] sigAlg = Der.sequence(Der.oid(OID_ECDSA_SHA256));
        byte[] tbs = Der.sequence(
                Der.tlv(0xa0, Der.integer(2)), // version 3
                Der.integer(serial),
                sigAlg,
                issuerDn,
                Der.sequence(Der.utcTime(new Date(notBefore)), Der.utcTime(new Date(notAfter))),
                subjectDn,
                subjectKey.getEncoded(), // already a complete SubjectPublicKeyInfo
                Der.tlv(0xa3, extensions));
        return Der.sequence(tbs, sigAlg, Der.bitString(signer.sign(tbs)));
    }

    /** SHA-1 of the key bits, the usual "key identifier". */
    private static byte[] keyId(PublicKey key) throws Exception {
        byte[] spki = key.getEncoded();
        // SubjectPublicKeyInfo = SEQUENCE { AlgorithmIdentifier, BIT STRING key }: hash the key bits.
        int i = 0;
        i += lengthOfHeader(spki, i); // outer sequence header
        int algLen = contentLength(spki, i);
        i += lengthOfHeader(spki, i) + algLen; // skip the algorithm identifier
        int h = lengthOfHeader(spki, i);
        int n = contentLength(spki, i);
        byte[] bits = new byte[n - 1];
        System.arraycopy(spki, i + h + 1, bits, 0, n - 1); // drop the "unused bits" byte
        return MessageDigest.getInstance("SHA-1").digest(bits);
    }

    private static int lengthOfHeader(byte[] b, int i) {
        int l = b[i + 1] & 0xff;
        return l < 128 ? 2 : 2 + (l & 0x7f);
    }

    private static int contentLength(byte[] b, int i) {
        int l = b[i + 1] & 0xff;
        if (l < 128) return l;
        int n = l & 0x7f, v = 0;
        for (int k = 0; k < n; k++) v = (v << 8) | (b[i + 2 + k] & 0xff);
        return v;
    }

    public X509Certificate caCertificate() {
        return caCert;
    }

    private byte[] leafCertificate(String host, long now) throws Exception {
        boolean isIp = host.matches("[0-9.]+") || host.contains(":");
        byte[] san = isIp
                ? Der.tlv(0x87, InetAddress.getByName(host).getAddress())
                : Der.tlv(0x82, host.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        byte[] exts = Der.sequence(
                Der.extension("2.5.29.19", true, Der.sequence()), // not a CA
                Der.extension("2.5.29.15", true, Der.bitString(new byte[] {(byte) 0x80}, 7)), // digitalSignature
                Der.extension("2.5.29.37", false, Der.sequence(Der.oid("1.3.6.1.5.5.7.3.1"))), // serverAuth
                Der.extension("2.5.29.17", false, Der.sequence(san)),
                Der.extension("2.5.29.14", false, Der.octetString(keyId(leafKeys.getPublic()))),
                Der.extension("2.5.29.35", false, Der.sequence(Der.tlv(0x80, caKeyId))));
        return certificate(signer, new BigInteger(120, random).add(BigInteger.ONE), caSubjectDn,
                Der.name(OID_CN, host.length() > 60 ? host.substring(0, 60) : host), leafKeys.getPublic(),
                now - BACKDATE_MS, now + LEAF_VALID_MS, exts);
    }

    /** A fresh certificate for host, signed by this CA (also used by the checks). */
    X509Certificate issueLeaf(String host, long now) throws Exception {
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(leafCertificate(host, now)));
    }

    /** A TLS server context that presents a certificate for exactly `host`, signed by this CA. */
    public synchronized SSLContext serverContextFor(String host) throws Exception {
        long now = System.currentTimeMillis();
        Entry e = cache.get(host);
        if (e != null && e.expires > now + 3600_000L) return e.ctx;
        X509Certificate leaf = issueLeaf(host, now);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(new javax.net.ssl.KeyManager[] {new SingleKeyManager(leafKeys.getPrivate(), new X509Certificate[] {leaf})}, null, null);
        if (cache.size() > 500) cache.clear();
        cache.put(host, new Entry(ctx, now + LEAF_VALID_MS));
        return ctx;
    }

    private static final class SingleKeyManager extends X509ExtendedKeyManager {
        private final PrivateKey key;
        private final X509Certificate[] chain;

        SingleKeyManager(PrivateKey key, X509Certificate[] chain) {
            this.key = key;
            this.chain = chain;
        }

        @Override public String[] getClientAliases(String t, Principal[] i) { return null; }
        @Override public String chooseClientAlias(String[] t, Principal[] i, Socket s) { return null; }
        @Override public String[] getServerAliases(String t, Principal[] i) { return new String[] {"leaf"}; }
        @Override public String chooseServerAlias(String t, Principal[] i, Socket s) { return "leaf"; }
        @Override public X509Certificate[] getCertificateChain(String a) { return chain; }
        @Override public PrivateKey getPrivateKey(String a) { return key; }
    }
}
