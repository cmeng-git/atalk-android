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

public class ParametersForSkein implements CipherParameters {

    public final static int Skein256 = 256;
    public final static int Skein512 = 512;
    public final static int Skein1024 = 1024;
    
    private int macSize;
    private int stateSize;
    private CipherParameters parameters;

    /**
     * Skein parameters for Skein MAC.
     * 
     * @param parameters
     *     This must be a KeyParameter instance that holds the key. 
     * @param stateSize
     *     The Skein state size to use.
     * @param macSize
     *     The requested Skein MAC output size in bits.
     */
    public ParametersForSkein(
            CipherParameters    parameters,
            int                 stateSize,
            int                 macSize)
    {
        this.macSize = macSize;
        this.stateSize = stateSize;
        this.parameters = parameters;
    }

    public int getMacSize()
    {
        return macSize;
    }

    public int getStateSize()
    {
        return stateSize;
    }

    public CipherParameters getParameters()
    {
        return parameters;
    }


}
