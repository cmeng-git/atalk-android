package org.atalk.android.util.javax.sound.sampled;

import java.io.IOException;
import java.net.URL;

public class AudioSystem
{
    /**
     * An integer that stands for an unknown numeric value.
     * This value is appropriate only for signed quantities that do not
     * normally take negative values.  Examples include file sizes, frame
     * sizes, buffer sizes, and sample rates.
     * A number of Java Sound constructors accept
     * a value of <code>NOT_SPECIFIED</code> for such parameters.  Other
     * methods may also accept or return this value, as documented.
     */
    public static final int NOT_SPECIFIED = -1;

    /**
     * Obtains an audio input stream from the URL provided.  The URL must
     * point to valid audio file data.
     *
     * @param url
     *         the URL for which the <code>AudioInputStream</code> should be
     *         constructed
     * @return an <code>AudioInputStream</code> object based on the audio file data pointed
     * to by the URL
     * @throws UnsupportedAudioFileException
     *         if the URL does not point to valid audio
     *         file data recognized by the system
     * @throws IOException
     *         if an I/O exception occurs
     */
    public static AudioInputStream getAudioInputStream(URL url)
            throws UnsupportedAudioFileException, IOException
    {
        return null;
    }

    public static AudioFormat[] getTargetFormats(AudioFormat.Encoding targetEncoding, AudioFormat sourceFormat)
    {
        return new AudioFormat[0];
    }
}
