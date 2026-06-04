/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.FileNotFoundException;
import java.io.InputStream;

import javax.media.format.AudioFormat;

import org.atalk.android.aTalkApp;
import org.atalk.impl.androidresources.AppResourceServiceImpl;

import timber.log.Timber;

/**
 * Utils that obtain audio resource input stream and its format.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class AudioStreamUtils {
    /**
     * Obtains an audio input stream from the URL provided.
     *
     * @param uri a valid url to a sound resource.
     *
     * @return the input stream to audio data.
     */
    public static InputStream getAudioInputStream(String uri) {
        InputStream audioStream = null;
        try {
            Context context = aTalkApp.getInstance();
            String resourceUri = getResourceUri(context, uri);
            audioStream = context.getContentResolver().openInputStream(Uri.parse(resourceUri));
        }
        catch (FileNotFoundException t) {
            Timber.e(t, "Error opening file: %s", uri);
        }
        return audioStream;
    }

    @NonNull
    private static String getResourceUri(Context context, String uri) {
        // Android resources don't use file extensions, remove if there is one.
        int lastPathSeparator = uri.lastIndexOf('/');
        int extensionStartIx;
        String resourceUri;

        if ((lastPathSeparator > -1)
                && ((extensionStartIx = uri.lastIndexOf('.')) > lastPathSeparator))
            resourceUri = uri.substring(0, extensionStartIx);
        else
            resourceUri = uri;

        // Must convert to proper androidResource for content access to aTalk raw/*.wav
        if (uri.startsWith(AppResourceServiceImpl.PROTOCOL)) {
            resourceUri = "android.resource://" + context.getPackageName() + "/" + resourceUri;
        }
        return resourceUri;
    }

    /**
     * Returns the audio format for the WAV <code>InputStream</code>. Or null if format cannot be obtained.
     *
     * @param audioInputStream the input stream.
     *
     * @return the format of the audio stream.
     */
    public static AudioFormat getFormat(InputStream audioInputStream) {
        WaveHeader waveHeader = new WaveHeader(audioInputStream);
        return new AudioFormat(AudioFormat.LINEAR,
                waveHeader.getSampleRate(), waveHeader.getBitsPerSample(), waveHeader.getChannels());
    }
}
