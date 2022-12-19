/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.impl.neomedia.transform.dtls;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.tls.Certificate;
import org.bouncycastle.tls.SignatureAlgorithm;

import java.io.IOException;

import timber.log.Timber;

/**
 * Bundles information such as key pair, hash function, fingerprint, etc. about
 * the certificate with which the local endpoint represented by this instance
 * authenticates its ends of DTLS sessions.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class CertificateInfo
{
    /**
     * The certificate with which the local endpoint represented by this
     * instance authenticates its ends of DTLS sessions.
     */
    private final Certificate certificate;

    /**
     * Certificate Signature Algorithm.
     */
    private final Short certificateType;

    /**
     * The private and public keys of {@link #certificate}.
     */
    private final AsymmetricCipherKeyPair keyPair;

    /**
     * The fingerprint of {@link #certificate}.
     */
    public final String localFingerprint;

    /**
     * The hash function of {@link #localFingerprint} (which is the same as the digest algorithm
     * of the signature algorithm of {@link #certificate} in accord with RFC 4572).
     */
    public final String localFingerprintHashFunction;

    /**
     * The timestamp (in milliseconds of system time) of the generation of this {@code CertificateInfo}.
     */
    public final long timestamp;

    /**
     * Initializes a new {@code CertificateInfo} instance.
     *
     * @param keyPair the private and public keys of {@code certificate}
     * @param certificate the certificate with which the local endpoint
     * represented by the new instance is to authenticate its ends of DTLS sessions
     * @param localFingerprintHashFunction hash function of localFingerprint
     * @param localFingerprint of the certificate
     * @param timestamp (ms) of the generation of this CertificateInfo.
     */
    public CertificateInfo(AsymmetricCipherKeyPair keyPair, Certificate certificate,
            String localFingerprintHashFunction, String localFingerprint, long timestamp)
    {
        this.keyPair = keyPair;
        this.certificate = certificate;

        short certSA = -1;
        try {
            certSA = certificate.getCertificateAt(0).getLegacySignatureAlgorithm();
        } catch (IOException e) {
            Timber.e("Certificate SignatureAlgorithm: %s", e.getMessage());
        }
        this.certificateType = SignatureAlgorithm.getClientCertificateType(certSA);

        this.localFingerprintHashFunction = localFingerprintHashFunction;
        this.localFingerprint = localFingerprint;
        this.timestamp = timestamp;
    }

    /**
     * Gets the certificate with which the local endpoint represented by this
     * instance authenticates its ends of DTLS sessions.
     *
     * @return the certificate with which the local endpoint represented by this
     * instance authenticates its ends of DTLS sessions.
     */
    public Certificate getCertificate()
    {
        return certificate;
    }

    public Short getCertificateType()
    {
        return certificateType;
    }

    /**
     * Gets the private and public keys of {@link #certificate}.
     *
     * @return the private and public keys of {@link #certificate}.
     */
    public AsymmetricCipherKeyPair getKeyPair()
    {
        return keyPair;
    }
}
