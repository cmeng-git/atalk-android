/*
Copyright (c) 2010 Werner Dittmann

Permission is hereby granted, free of charge, to any person
obtaining a copy of this software and associated documentation
files (the "Software"), to deal in the Software without
restriction, including without limitation the rights to use,
copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the
Software is furnished to do so, subject to the following
conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
OTHER DEALINGS IN THE SOFTWARE.

The tweaks and modifications for Java:
Copyright (c) 2010, Werner Dittmann. 

The same permissions granted.
 */
package org.atalk.bccontrib.macs;

import org.atalk.bccontrib.digests.Skein;
import org.atalk.bccontrib.params.ParametersForSkein;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.Mac;
import org.bouncycastle.crypto.params.KeyParameter;

public class SkeinMac implements Mac {

    private Skein skein;

    private long[] Xsave;

    public SkeinMac() {
    }

    public void init(CipherParameters params) throws IllegalArgumentException {
        ParametersForSkein p = (ParametersForSkein) params;
        KeyParameter kp = (KeyParameter) (p.getParameters());

        skein = new Skein(p.getStateSize(), p.getMacSize(), 0, kp.getKey());
        Xsave = skein.getState();
    }

    public String getAlgorithmName() {
        return skein.getAlgorithmName() + "/MAC";
    }

    public int getMacSize() {
        return skein.getDigestSize();
    }

    public void update(byte in) throws IllegalStateException {
        skein.update(in);
    }

    /**
     * Update the MAC with a message bit string.
     * 
     * Skein can handle data not only as bytes but also as bit strings of
     * arbitrary length (up to its maximum design size).
     * 
     * @param in
     *     The byte array that holds the bit string. The array must be big
     *     enough to hold all bits.
     * @param inOff
     *     Offset into byte array where the data starts, must be a byte number
     *     (not a bit number).
     * @param len
     *     Number of bits to hash.
     */
    public void updateBits(byte[] in, int inOff, int len)
            throws DataLengthException, IllegalStateException {
        skein.updateBits(in, inOff, len);
    }

    public void update(byte[] in, int inOff, int len)
            throws DataLengthException, IllegalStateException {
        skein.update(in, inOff, len);
    }

    public int doFinal(byte[] out, int outOff) throws DataLengthException,
            IllegalStateException {
        int len = skein.doFinal(out, outOff);
        reset();
        return len;
    }

    public void reset() {
        skein.initialize(Xsave);
    }

}
