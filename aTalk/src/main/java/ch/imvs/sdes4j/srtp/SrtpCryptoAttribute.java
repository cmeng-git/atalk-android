package ch.imvs.sdes4j.srtp;

import ch.imvs.sdes4j.CryptoAttribute;

/**
 * Security descriptions attribute for SRTP media streams.
 * 
 * @author Ingo Bauersachs
 */
public class SrtpCryptoAttribute extends CryptoAttribute {
    SrtpCryptoAttribute(){
    }

    /**
     * Creates an SRTP crypto attribute from its textual representation.
     * 
     * @param encoded The textual representation of the attribute.
     * @return The parsed crypto data.
     */
    public static SrtpCryptoAttribute create(String encoded){
        return (SrtpCryptoAttribute)CryptoAttribute.create(encoded, new SrtpSDesFactory());
    }

    /**
     * Creates an instance of a SrtpCryptoAttribute from SDES attributes (tag,
     * crypto suite, key params and session params).
     *
     * @param tag unparsed tag as a string. 
     * @param cryptoSuite the crypto suite as an unparsed string.
     * @param keyParams An unparsed string representation of the key param list
     * (each key must be separated by a ";").
     * @param sessionParams An unparsed string representation of the session
     * param list (each key must be separated by a " ").
     *
     * @return a parsed SRTP crypto attribute.
     */
    public static SrtpCryptoAttribute create(String tag, String cryptoSuite, String keyParams, String sessionParams){
        return (SrtpCryptoAttribute) CryptoAttribute.create(tag, cryptoSuite, keyParams, sessionParams, new SrtpSDesFactory());
    }

    /**
     * Creates a crypto attribute from already instantiated objects.
     * 
     * @param tag identifier for this particular crypto attribute
     * @param cryptoSuite identifier that describes the encryption and
     *            authentication algorithms
     * @param keyParams one or more sets of keying material
     * @param sessionParams the additional key parameters
     */
    public SrtpCryptoAttribute(int tag, SrtpCryptoSuite cryptoSuite, SrtpKeyParam[] keyParams, SrtpSessionParam[] sessionParams) {
        super(tag, cryptoSuite, keyParams, sessionParams == null ? new SrtpSessionParam[0] : sessionParams);
    }

    @Override
    public SrtpCryptoSuite getCryptoSuite() {
        return (SrtpCryptoSuite) super.getCryptoSuite();
    }

    @Override
    public SrtpKeyParam[] getKeyParams() {
        return (SrtpKeyParam[]) super.getKeyParams();
    }

    @Override
    public SrtpSessionParam[] getSessionParams() {
        return (SrtpSessionParam[]) super.getSessionParams();
    }
}
