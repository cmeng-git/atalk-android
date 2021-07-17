package org.atalk.android.util.javax.sound.sampled;

import java.io.IOException;
import java.io.InputStream;

public class AudioInputStream
        extends InputStream
{
    public int read()
            throws IOException
    {
        return 0;
    }

    public AudioFormat getFormat()
    {
        return null;
    }

    public int available()
            throws IOException
    {
        return 0;
    }
}

