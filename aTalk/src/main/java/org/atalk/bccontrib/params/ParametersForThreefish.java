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
*/

package org.atalk.bccontrib.params;

import org.bouncycastle.crypto.CipherParameters;

public class ParametersForThreefish implements CipherParameters {
    public final static int Threefish256 = 256;
    public final static int Threefish512 = 512;
    public final static int Threefish1024 = 1024;
    
    private int stateSize;
    private CipherParameters parameters;
    private long[] tweak;

    public ParametersForThreefish(
            CipherParameters    parameters,
            int                 stateSize,
            long[]              tweak)
    {
        this.stateSize = stateSize;
        this.parameters = parameters;
        if (tweak != null) {
            this.tweak = new long[2];
            this.tweak[0] = tweak[0];
            this.tweak[1] = tweak[1];
        }
    }

    /**
     * @return the stateSize
     */
    public int getStateSize() {
        return stateSize;
    }

    /**
     * @return the parameters
     */
    public CipherParameters getParameters() {
        return parameters;
    }

    /**
     * @return the tweak
     */
    public long[] getTweak() {
        return tweak;
    }

}
