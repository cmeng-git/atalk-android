/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.impl.androidcertdialog;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.atalk.android.R;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.*;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.*;

import javax.security.auth.x500.X500Principal;

import timber.log.Timber;

/**
 * Form that shows the content of an X509Certificate.
 *
 * * @author Eng Chong Meng
 */
public class X509CertificateView extends Dialog
{
    private static final String BYTE_FORMAT = "%02x:";
    private Certificate certificate;
    private Context mContext;

    /**
     * Constructs a X509 certificate form. Mainly use by external to format certificate to html string
     */
    public X509CertificateView(Context context)
    {
        super(context);
        mContext = context;
    }

    /**
     * Constructs a X509 certificate form from certificate[] chain
     * Default to assume only one certificate in the certificate chain.
     *
     * @param certificates <tt>X509Certificate</tt> object
     */
    public X509CertificateView(Context context, Certificate[] certificates)
    {
        super(context);
        mContext = context;
        this.certificate = certificates[0];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.x509_certificate_view);
        setTitle(mContext.getString(R.string.service_gui_CERT_INFO_CHAIN));

        WebView certInfo = findViewById(R.id.certificateInfo);
        WebSettings settings = certInfo.getSettings();
        settings.setDefaultFontSize(10);
        settings.setDefaultFixedFontSize(10);
        settings.setBuiltInZoomControls(true);

        // android API-29 cannot handle character "#", so replaced it with "&sharp;"
        String certHtml = toString(certificate).replace("#", "&sharp;");
        certInfo.loadData(certHtml, "text/html", "utf-8");
    }

    /**
     * Creates a html String representation of the given object.
     *
     * @param certificate to print
     * @return the String representation
     */
    public String toString(Object certificate)
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("<html><body>\n");

        if (certificate instanceof X509Certificate) {
            renderX509(sb, (X509Certificate) certificate);
        }
        else {
            sb.append("<pre>\n");
            sb.append(certificate.toString());
            sb.append("</pre>\n");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    /**
     * Appends an HTML representation of the given X509Certificate.
     *
     * @param sb StringBuilder to append to
     * @param certificate to print
     */
    private void renderX509(StringBuilder sb, X509Certificate certificate)
    {
        Map<String, String> rdnNames;
        X500Principal issuer = certificate.getIssuerX500Principal();
        X500Principal subject = certificate.getSubjectX500Principal();

        sb.append("<table cellspacing='1' cellpadding='1'>\n");

        // subject
        addTitle(sb, mContext.getString(R.string.service_gui_CERT_INFO_ISSUED_TO));
        rdnNames = splitRdn(subject.getName());
        if (!rdnNames.isEmpty()) {
            for (Map.Entry<String, String> name : rdnNames.entrySet()) {
                String nameType = name.getKey();
                String lblKey = "service_gui_CERT_INFO_" + nameType;

                String lbl;
                int resID = mContext.getResources().getIdentifier(lblKey, "string", mContext.getPackageName());
                try {
                    lbl = mContext.getString(resID);
                } catch (Resources.NotFoundException e) {
                    Timber.w("Unknown certificate subject label: %s", nameType);
                    lbl = nameType;
                }
                if (("!" + lblKey + "!").equals(lbl))
                    lbl = nameType;

                String value = name.getValue();
                addField(sb, lbl, value);
            }
        }
        else {
            addField(sb, mContext.getString(R.string.service_gui_CERT_INFO_CN), subject.getName());
        }

        // issuer
        addTitle(sb, mContext.getString(R.string.service_gui_CERT_INFO_ISSUED_BY));
        rdnNames = splitRdn(issuer.getName());
        if (!rdnNames.isEmpty()) {
            for (Map.Entry<String, String> name : rdnNames.entrySet()) {
                String nameType = name.getKey();
                String lblKey = "service_gui_CERT_INFO_" + nameType;

                String lbl;
                int resID = mContext.getResources().getIdentifier(lblKey, "string", mContext.getPackageName());
                try {
                    lbl = mContext.getString(resID);
                } catch (Resources.NotFoundException e) {
                    Timber.w("Unknown certificate issuer label: %s", nameType);
                    lbl = nameType;
                }
                if (("!" + lblKey + "!").equals(lbl))
                    lbl = nameType;

                String value = name.getValue();
                addField(sb, lbl, value);
            }
        }
        else {
            addField(sb, mContext.getString(R.string.service_gui_CERT_INFO_CN), issuer.getName());
        }

        // validity
        addTitle(sb, mContext.getString(R.string.service_gui_CERT_INFO_VALIDITY));
        addField(sb, mContext.getString(R.string.service_gui_CERT_INFO_ISSUED_ON), certificate.getNotBefore().toString());
        addField(sb, mContext.getString(R.string.service_gui_CERT_INFO_EXPIRES_ON), certificate.getNotAfter().toString());

        addTitle(sb, mContext.getString(R.string.service_gui_CERT_INFO_FINGERPRINTS));
        try {
            String sha256String = getThumbprint(certificate, "SHA-256");
            addField(sb, "SHA256:", sha256String, 48);

            String sha1String = getThumbprint(certificate, "SHA1");
            addField(sb, "SHA1:", sha1String, 48);
        } catch (CertificateException e) {
            // do nothing as we cannot show this value
        }

        addTitle(sb, mContext.getString(R.string.service_gui_CERT_INFO_CERT_DETAILS));
        addField(sb, mContext.getString(R.string.service_gui_CERT_INFO_SER_NUM), certificate.getSerialNumber().toString());
        addField(sb, mContext.getString(R.string.service_gui_CERT_INFO_VER), String.valueOf(certificate.getVersion()));
        addField(sb, mContext.getString(R.string.service_gui_CERT_INFO_SIGN_ALG), String.valueOf(certificate.getSigAlgName()));
        addTitle(sb, mContext.getString(R.string.service_gui_CERT_INFO_PUB_KEY_INFO));
        addField(sb, mContext.getString(R.string.service_gui_CERT_INFO_ALG), certificate.getPublicKey().getAlgorithm());

        if (certificate.getPublicKey().getAlgorithm().equals("RSA")) {
            RSAPublicKey key = (RSAPublicKey) certificate.getPublicKey();

            addField(sb, mContext.getString(R.string.service_gui_CERT_INFO_PUB_KEY),
                    mContext.getString(R.string.service_gui_CERT_INFO_KEY_BITS_PRINT,
                            String.valueOf((key.getModulus().toByteArray().length - 1) * 8)),
                    getHex(key.getModulus().toByteArray()), 48);

            addField(sb, mContext.getString(R.string.service_gui_CERT_INFO_EXP),
                    key.getPublicExponent().toString());

            addField(sb, mContext.getString(R.string.service_gui_CERT_INFO_KEY_SIZE),
                    mContext.getString(R.string.service_gui_CERT_INFO_KEY_BITS_PRINT,
                            String.valueOf(key.getModulus().bitLength())));
        }
        else if (certificate.getPublicKey().getAlgorithm().equals("DSA")) {
            DSAPublicKey key = (DSAPublicKey) certificate.getPublicKey();
            addField(sb, "Y:", key.getY().toString(16));
        }

        addField(sb, mContext.getString(R.string.service_gui_CERT_INFO_SIGN),
                mContext.getString(R.string.service_gui_CERT_INFO_KEY_BITS_PRINT,
                        String.valueOf(certificate.getSignature().length * 8)),
                getHex(certificate.getSignature()), 48);
        sb.append("</table>\n");
    }

    /**
     * Add a title.
     *
     * @param sb StringBuilder to append to
     * @param title to print
     */
    private void addTitle(StringBuilder sb, String title)
    {
        sb.append("<tr><td colspan='2'")
                .append(" style='margin-top: 10pt; white-space: nowrap'><p><b>")
                .append(title).append("</b></p></td></tr>\n");
    }

    /**
     * Add a field.
     *
     * @param sb StringBuilder to append to
     * @param field name of the certificate field
     * @param value to print
     */
    private void addField(StringBuilder sb, String field, String value)
    {
        addField(sb, field, value, null, 0);
    }

    /**
     * Add a field.
     *
     * @param sb StringBuilder to append to
     * @param field name of the certificate field
     * @param value to print
     * @param wrap force-wrap after number of characters
     */
    private void addField(StringBuilder sb, String field, String value, int wrap)
    {
        addField(sb, field, value, null, wrap);
    }

    /**
     * Add a field.
     *
     * @param sb StringBuilder to append to
     * @param field name of the certificate field
     * @param value to print (not wrapped)
     * @param otherValue second line of value to print (wrapped)
     * @param wrap force-wrap after number of characters
     */
    private void addField(StringBuilder sb, String field, String value, String otherValue, int wrap)
    {
        // use &bull; instead of &#8226; as sdk-29+ webview cannot accept &#xxxx coding.
        sb.append("<tr><td style='margin-right: 25pt;")
                .append("white-space: nowrap' valign='top'>&bull; ")
                .append(field).append("</td><td><span");

        if (otherValue != null) {
            sb.append('>').append(value).append("</span><br/><span");
            value = otherValue;
        }

        if (wrap > 0) {
            sb.append(" style='font-family:monospace'>");
            for (int i = 0; i < value.length(); i++) {
                if (i % wrap == 0 && i > 0) {
                    sb.append("<br/>");
                }
                sb.append(value.charAt(i));
            }
        }
        else {
            sb.append(">");
            sb.append(value);
        }
        sb.append("</span></td></tr>");
    }

    /**
     * Converts the byte array to hex string.
     *
     * @param raw the data.
     * @return the hex string.
     */
    private String getHex(byte[] raw)
    {
        if (raw == null)
            return null;

        StringBuilder hex = new StringBuilder(2 * raw.length);
        try (Formatter f = new Formatter(hex)) {
            for (byte b : raw)
                f.format(BYTE_FORMAT, b);
        }
        return hex.substring(0, hex.length() - 1);
    }

    /**
     * Calculates the hash of the certificate known as the "thumbprint" and returns it as a string representation.
     *
     * @param cert The certificate to hash.
     * @param algorithm The hash algorithm to use.
     * @return The SHA-1 hash of the certificate.
     * @throws CertificateException if exception
     */
    private static String getThumbprint(X509Certificate cert, String algorithm)
            throws CertificateException
    {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new CertificateException(e);
        }

        byte[] encodedCert = cert.getEncoded();
        StringBuilder sb = new StringBuilder(encodedCert.length * 2);
        try (Formatter f = new Formatter(sb)) {
            for (byte b : digest.digest(encodedCert))
                f.format(BYTE_FORMAT, b);
        }

        return sb.substring(0, sb.length() - 1);
    }

    /**
     * @param rdnNames attribute strings with "," as seperator
     * @return Map of keys and values for each attribute
     */
    private static Map<String, String> splitRdn(String rdnNames)
    {
        Map<String, String> rdn_pairs = new LinkedHashMap<>();
        String[] pairs = rdnNames.split(",");

        for (String pair : pairs) {
            final int idx = pair.indexOf("=");
            if (idx > 0)
                rdn_pairs.put(pair.substring(0, idx), pair.substring(idx + 1));
        }
        return rdn_pairs;
    }

    /*
     * Construct a "simplified name" based on the subject DN from the
     * certificate. The purpose is to have something shorter to display in the
     * list. The name used is one of the following DN parts, if
     * available, otherwise the complete DN: 'CN', 'OU' or else 'O'.
     *
     * @param cert to read subject DN from
     * @return the simplified name
     */
    //    private static String getSimplifiedName(X509Certificate cert)
    //    {
    //        final HashMap<String, String> parts = new HashMap<>();
    //        try {
    //            for (Rdn name : new LdapName(cert.getSubjectX500Principal().getName()).getRdns()) {
    //                if (name.getType() != null && name.getValue() != null) {
    //                    parts.put(name.getType(), name.getValue().toString());
    //                }
    //            }
    //        } catch (InvalidNameException ex) // NOPMD
    //        {
    //            ex.printStackTrace();
    //        }
    //
    //        String result = parts.get("CN");
    //        if (result == null) {
    //            result = parts.get("OU");
    //        }
    //        if (result == null) {
    //            result = parts.get("O");
    //        }
    //        if (result == null) {
    //            result = cert.getSubjectX500Principal().getName();
    //        }
    //        return result;
    //    }
}
