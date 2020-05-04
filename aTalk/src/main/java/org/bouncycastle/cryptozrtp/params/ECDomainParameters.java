package org.bouncycastle.cryptozrtp.params;

import gnu.java.bigintcrypto.BigIntegerCrypto;
import org.bouncycastle.mathzrtp.ec.ECConstants;
import org.bouncycastle.mathzrtp.ec.ECCurve;
import org.bouncycastle.mathzrtp.ec.ECPoint;

public class ECDomainParameters
    implements ECConstants
{
    ECCurve     curve;
    byte[]      seed;
    ECPoint     G;
    BigIntegerCrypto  n;
    BigIntegerCrypto  h;

    public ECDomainParameters(
        ECCurve     curve,
        ECPoint     G,
        BigIntegerCrypto  n)
    {
        this.curve = curve;
        this.G = G;
        this.n = n;
        this.h = ONE;
        this.seed = null;
    }

    public ECDomainParameters(
        ECCurve     curve,
        ECPoint     G,
        BigIntegerCrypto  n,
        BigIntegerCrypto  h)
    {
        this.curve = curve;
        this.G = G;
        this.n = n;
        this.h = h;
        this.seed = null;
    }

    public ECDomainParameters(
        ECCurve     curve,
        ECPoint     G,
        BigIntegerCrypto  n,
        BigIntegerCrypto  h,
        byte[]      seed)
    {
        this.curve = curve;
        this.G = G;
        this.n = n;
        this.h = h;
        this.seed = seed;
    }

    // Convert from "classic" parameters to cryptozrtp/mathzrtp parameters
    public ECDomainParameters(
            org.bouncycastle.math.ec.ECCurve     curveIn,
            org.bouncycastle.math.ec.ECPoint     GIn,
            java.math.BigInteger  nIn,
            java.math.BigInteger  hIn,
            byte[]      seed)
        {
            org.bouncycastle.math.ec.ECCurve.Fp fpCurve =
                (org.bouncycastle.math.ec.ECCurve.Fp)curveIn;
            BigIntegerCrypto q = new BigIntegerCrypto(fpCurve.getQ().toByteArray());
            BigIntegerCrypto a = new BigIntegerCrypto(curveIn.getA().toBigInteger().toByteArray());
            BigIntegerCrypto b = new BigIntegerCrypto(curveIn.getB().toBigInteger().toByteArray());
            this.curve = new ECCurve.Fp(q, a, b);

            byte[] encoded = GIn.getEncoded(false);
            this.G = this.curve.decodePoint(encoded);
            this.n = new BigIntegerCrypto(nIn.toByteArray());
            this.h = new BigIntegerCrypto(hIn.toByteArray());
            this.seed = seed;
        }

    public ECCurve getCurve()
    {
        return curve;
    }

    public ECPoint getG()
    {
        return G;
    }

    public BigIntegerCrypto getN()
    {
        return n;
    }

    public BigIntegerCrypto getH()
    {
        return h;
    }

    public byte[] getSeed()
    {
        return seed;
    }
}
