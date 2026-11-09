package com.familymdm.agent.inspect;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Plain checks for the hand-written certificate code. Run on a computer:
 *   javac -d out android/sitepolicy/src/main/java/com/familymdm/agent/inspect/*.java android/sitepolicy/src/test/java/com/familymdm/agent/inspect/CertCheck.java
 *   java -cp out com.familymdm.agent.inspect.CertCheck
 */
public class CertCheck {
    static int failures = 0;

    static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        } else {
            System.out.println("ok:   " + what);
        }
    }

    static CertAuthority.Signer signerFor(KeyPair kp) {
        return data -> {
            Signature s = Signature.getInstance("SHA256withECDSA");
            s.initSign(kp.getPrivate());
            s.update(data);
            return s.sign();
        };
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair caKeys = g.generateKeyPair();
        long now = System.currentTimeMillis();

        byte[] caDer = CertAuthority.newCaCertificate(signerFor(caKeys), caKeys.getPublic(), "MDM test CA", now);
        X509Certificate ca = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new java.io.ByteArrayInputStream(caDer));
        check(ca.getSubjectX500Principal().getName().contains("MDM test CA"), "the CA certificate parses and has its name");
        check(ca.getBasicConstraints() == 0, "the CA may sign certificates but not other CAs (path length 0)");
        check(ca.getKeyUsage() != null && ca.getKeyUsage()[5], "the CA's key usage allows signing certificates");
        ca.verify(caKeys.getPublic());
        check(true, "the CA certificate is correctly self-signed");
        check(ca.getNotBefore().getTime() < now && ca.getNotAfter().getTime() > now + 5L * 365 * 24 * 3600 * 1000, "the CA is valid now and for years");

        CertAuthority auth = new CertAuthority(signerFor(caKeys), caDer);
        X509Certificate leaf = auth.issueLeaf("www.example.test", now);
        leaf.verify(ca.getPublicKey());
        check(true, "a site certificate is signed by the CA");
        check(leaf.getBasicConstraints() == -1, "a site certificate is not a CA");
        check(leaf.getKeyUsage() != null && leaf.getKeyUsage()[0], "a site certificate may sign TLS handshakes");
        check(leaf.getExtendedKeyUsage() != null && leaf.getExtendedKeyUsage().contains("1.3.6.1.5.5.7.3.1"), "a site certificate is for TLS servers");
        List<List<?>> san = new java.util.ArrayList<>(leaf.getSubjectAlternativeNames());
        check(san != null && san.size() == 1 && Integer.valueOf(2).equals(san.get(0).get(0)) && "www.example.test".equals(san.get(0).get(1)), "the site name is in the certificate's alternative names");
        check(leaf.getIssuerX500Principal().equals(ca.getSubjectX500Principal()), "the issuer is the CA");
        check(leaf.getNotAfter().getTime() - leaf.getNotBefore().getTime() < 400L * 24 * 3600 * 1000, "a site certificate lives well under the 398-day browser limit");

        // The same check a browser does: build the chain from the site certificate up to the CA and validate it.
        PKIXParameters p = new PKIXParameters(Collections.singleton(new TrustAnchor(ca, null)));
        p.setRevocationEnabled(false);
        CertPathValidator.getInstance("PKIX").validate(CertificateFactory.getInstance("X.509").generateCertPath(Arrays.asList(leaf)), p);
        check(true, "the chain validates with the CA as the only trusted root");

        X509Certificate ip = auth.issueLeaf("127.0.0.1", now);
        List<List<?>> ipSan = new java.util.ArrayList<>(ip.getSubjectAlternativeNames());
        check(ipSan != null && Integer.valueOf(7).equals(ipSan.get(0).get(0)), "an IP address becomes an IP alternative name");

        // A different CA must not be able to vouch for these certificates.
        KeyPair other = g.generateKeyPair();
        boolean rejected = false;
        try {
            leaf.verify(other.getPublic());
        } catch (Exception e) {
            rejected = true;
        }
        check(rejected, "another phone's CA cannot vouch for this phone's certificates");

        // Contexts are cached per site, and a different site gets its own.
        check(auth.serverContextFor("a.example.test") == auth.serverContextFor("a.example.test"), "the same site reuses its certificate");
        check(auth.serverContextFor("a.example.test") != auth.serverContextFor("b.example.test"), "another site gets another certificate");

        System.out.println(failures == 0 ? "ALL OK" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
