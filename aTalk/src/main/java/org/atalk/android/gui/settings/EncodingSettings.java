/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.settings;

import android.os.Bundle;
import android.view.KeyEvent;

import java.util.List;

import org.atalk.android.R;
import org.atalk.android.gui.account.settings.MediaEncodingActivity;
import org.atalk.android.gui.account.settings.MediaEncodingsFragment;
import org.atalk.impl.neomedia.MediaServiceImpl;
import org.atalk.impl.neomedia.NeomediaActivator;
import org.atalk.service.neomedia.codec.EncodingConfiguration;
import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.service.osgi.OSGiActivity;
import org.atalk.util.MediaType;

/**
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class EncodingSettings extends OSGiActivity {
    public static final String EXTRA_MEDIA_TYPE = "media_type";
    public static final String MEDIA_TYPE_AUDIO = "media_type.AUDIO";
    public static final String MEDIA_TYPE_VIDEO = "media_type.VIDEO";
    private MediaEncodingsFragment mMediaEncodings;
    private MediaType mMediaType;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String mediaTypeStr = getIntent().getStringExtra(EXTRA_MEDIA_TYPE);
        if (MEDIA_TYPE_AUDIO.equals(mediaTypeStr)) {
            mMediaType = MediaType.AUDIO;
            setMainTitle(R.string.settings_audio_codecs);
        }
        else if (MEDIA_TYPE_VIDEO.equals(mediaTypeStr)) {
            mMediaType = MediaType.VIDEO;
            setMainTitle(R.string.settings_video_codec);
        }

        if (savedInstanceState == null) {
            MediaServiceImpl mediaSrvc = NeomediaActivator.getMediaServiceImpl();
            if (mediaSrvc != null) {
                EncodingConfiguration encConfig = mediaSrvc.getCurrentEncodingConfiguration();

                List<MediaFormat> formats = MediaEncodingActivity.getEncodings(encConfig, mMediaType);
                List<String> encodings = MediaEncodingActivity.getEncodingsStr(formats.iterator());
                List<Integer> priorities = MediaEncodingActivity.getPriorities(formats, encConfig);

                mMediaEncodings = MediaEncodingsFragment.newInstance(encodings, priorities);
                getSupportFragmentManager().beginTransaction().add(android.R.id.content, mMediaEncodings).commit();
            }
        }
        else {
            mMediaEncodings = (MediaEncodingsFragment) getSupportFragmentManager().findFragmentById(android.R.id.content);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        MediaServiceImpl mediaSrvc = NeomediaActivator.getMediaServiceImpl();

        if ((keyCode == KeyEvent.KEYCODE_BACK) && (mediaSrvc != null)) {
            MediaEncodingActivity.commitPriorities(
                    NeomediaActivator.getMediaServiceImpl().getCurrentEncodingConfiguration(),
                    mMediaType, mMediaEncodings);
            finish();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }
}
