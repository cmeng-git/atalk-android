/*
 * hymnchtv: COG hymns' lyrics viewer and player client
 * Copyright 2020 Eng Chong Meng
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
package org.atalk.android.plugin.mediaplayer;

import static org.atalk.android.plugin.mediaplayer.YoutubePlayerFragment.rateMax;
import static org.atalk.android.plugin.mediaplayer.YoutubePlayerFragment.rateMin;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.exoplayer2.*;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.util.MimeTypes;

import net.java.sip.communicator.util.UtilActivator;

import org.apache.http.util.TextUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.persistance.FileBackend;
import org.atalk.service.configuration.ConfigurationService;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The class handles the actual content source address decoding for the user selected hymn
 * see https://developer.android.com/codelabs/exoplayer-intro#0
 *
 * This MediaExoPlayerFragment requires its parent FragmentActivity to handle onConfigurationChanged()
 * It does not consider onSaveInstanceState(); it uses the speed in the user configuration setting.
 *
 * @author Eng Chong Meng
 */
public class MediaExoPlayerFragment extends Fragment
{
    // Tag for the instance state bundle.
    public static final String ATTR_MEDIA_URL = "mediaUrl";
    public static final String ATTR_MEDIA_URLS = "mediaUrls";
    public static final String PREF_PLAYBACK_SPEED = "playBack_speed";

    private static final String sampleUrl = "https://www.learningcontainer.com/wp-content/uploads/2020/05/sample-mp4-file.mp4";

    // Default playback video url
    private String mediaUrl = sampleUrl;
    private ArrayList<String> mediaUrls = null;

    // Playback ratio of normal speed.
    private float mSpeed = 1.0f;

    private FragmentActivity mContext;
    private static final ConfigurationService configService = UtilActivator.getConfigurationService();

    private SimpleExoPlayer mSimpleExoPlayer = null;
    private StyledPlayerView mPlayerView;
    private PlaybackStateListener playbackStateListener;

    /**
     * Create a new instance of MediaExoPlayerFragment, providing "bundle" as an argument.
     */
    public static MediaExoPlayerFragment getInstance(Bundle args)
    {
        MediaExoPlayerFragment mExoPlayer = new MediaExoPlayerFragment();
        mExoPlayer.setArguments(args);
        return mExoPlayer;
    }

    @Override
    public void onAttach(@NonNull @NotNull Context context)
    {
        super.onAttach(context);
        mContext = (FragmentActivity) context;
    }

    @SuppressLint("CommitPrefEdits")
    @Override
    public void onCreate(@Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            // mediaUrl = "https://youtu.be/vCKCkc8llaM";  for testing only
            mediaUrl = args.getString(ATTR_MEDIA_URL);
            mediaUrls = args.getStringArrayList(ATTR_MEDIA_URLS);
        }
        playbackStateListener = new PlaybackStateListener();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
    {
        View mConvertView = inflater.inflate(R.layout.media_player_exo_ui, container, false);
        mPlayerView = mConvertView.findViewById(R.id.exoplayerView);

        if (container != null)
            container.setVisibility(View.VISIBLE);

        // Need to set text color in Hymnchtv; although ExoStyledControls.ButtonText specifies while
        TextView rewindButtonTextView = mConvertView.findViewById(com.google.android.exoplayer2.ui.R.id.exo_rew_with_amount);
        rewindButtonTextView.setTextColor(Color.WHITE);

        TextView fastForwardButtonTextView = mConvertView.findViewById(com.google.android.exoplayer2.ui.R.id.exo_ffwd_with_amount);
        fastForwardButtonTextView.setTextColor(Color.WHITE);

        return mConvertView;
    }

    @Override
    public void onResume()
    {
        super.onResume();
        // Load the media and start playback each time onResume() is called.
        initializePlayer();
    }

    @Override
    public void onPause()
    {
        super.onPause();
        releasePlayer();
    }

    public void initializePlayer()
    {
        if (mSimpleExoPlayer == null) {
            mSimpleExoPlayer = new SimpleExoPlayer.Builder(mContext).build();
            mSimpleExoPlayer.addListener(playbackStateListener);
            mPlayerView.setPlayer(mSimpleExoPlayer);
        }

        if ((mediaUrls == null) || mediaUrls.isEmpty()) {
            MediaItem mediaItem = buildMediaItem(mediaUrl);
            if (mediaItem != null)
                playMedia(mediaItem);
        }
        else {
            playVideoUrls();
        }
    }

    /**
     * Media play-back takes a lot of resources, so everything should be stopped and released at this time.
     * Release all media-related resources. In a more complicated app this
     * might involve unregistering listeners or releasing audio focus.
     *
     * Save the user defined playback speed
     */
    public void releasePlayer()
    {
        if (mSimpleExoPlayer != null) {
            mSpeed = mSimpleExoPlayer.getPlaybackParameters().speed;

            // Audio media player speed is (0.25 >= mSpeed <= 2.0)
            if (mSpeed >= rateMin && mSpeed <= rateMax) {
                configService.setProperty(PREF_PLAYBACK_SPEED, mSpeed);
            }

            mSimpleExoPlayer.setPlayWhenReady(false);
            mSimpleExoPlayer.removeListener(playbackStateListener);
            mSimpleExoPlayer.release();
            mSimpleExoPlayer = null;
        }
    }

    /**
     * Prepare and play the specified mediaItem
     *
     * @param mediaItem for playback
     */
    private void playMedia(MediaItem mediaItem)
    {
        if (mediaItem != null) {
            mSpeed = (float) configService.getDouble(PREF_PLAYBACK_SPEED, 1.0);

            setPlaybackSpeed(mSpeed);
            mSimpleExoPlayer.setMediaItem(mediaItem, 0);
            mSimpleExoPlayer.setPlayWhenReady(true);
            mSimpleExoPlayer.prepare();
        }
    }

    /**
     * Prepare and playback a list of given video URLs if not empty
     */
    private void playVideoUrls()
    {
        if ((mediaUrls != null) && !mediaUrls.isEmpty()) {
            List<MediaItem> mediaItems = new ArrayList<>();
            for (String tmpUrl : mediaUrls) {
                mediaItems.add(buildMediaItem(tmpUrl));
            }

            mSpeed = (float) configService.getDouble(PREF_PLAYBACK_SPEED, 1.0);
            setPlaybackSpeed(mSpeed);

            mSimpleExoPlayer.setMediaItems(mediaItems);
            mSimpleExoPlayer.setPlayWhenReady(true);
            mSimpleExoPlayer.prepare();
        }
    }

    /**
     * Play the specified videoUrl using android Intent.ACTION_VIEW
     *
     * @param videoUrl videoUrl not playable by ExoPlayer
     */
    private void playVideoUrlExt(String videoUrl)
    {
        // remove the exoPlayer fragment
        mContext.getSupportFragmentManager().beginTransaction().remove(this).commit();

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    /**
     * set SimpleExoPlayer playback speed
     *
     * @param speed playback speed: default 1.0f
     */
    private void setPlaybackSpeed(float speed)
    {
        PlaybackParameters playbackParameters = new PlaybackParameters(speed, 1.0f);
        if (mSimpleExoPlayer != null) {
            mSimpleExoPlayer.setPlaybackParameters(playbackParameters);
        }
    }

    /**
     * Build and return the mediaItem or
     * Proceed to play if it is a youtube link; return null;
     *
     * @param mediaUrl for building the mediaItem
     * @return built mediaItem
     */
    private MediaItem buildMediaItem(String mediaUrl)
    {
        if (TextUtils.isEmpty(mediaUrl))
            return null;

        MediaItem mediaItem;
        Uri uri = Uri.parse(mediaUrl);
        String mimeType = FileBackend.getMimeType(mContext, uri);
        if (!TextUtils.isEmpty(mimeType) && (mimeType.contains("video") || mimeType.contains("audio"))) {
            mediaItem = new MediaItem.Builder()
                    .setUri(mediaUrl)
                    .setMimeType(mimeType)
                    .build();
        }
        else {
            mediaItem = new MediaItem.Builder()
                    .setUri(mediaUrl)
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .build();
        }
        return mediaItem;
    }

    /**
     * ExoPlayer playback state listener
     */
    private class PlaybackStateListener implements Player.Listener
    {
        @Override
        public void onPlaybackStateChanged(int playbackState)
        {
            switch (playbackState) {
                case ExoPlayer.STATE_IDLE:
                    aTalkApp.showToastMessage(R.string.gui_playback_error);
                    // Attempt to use android player if exoplaer failed to play
                    playVideoUrlExt(mediaUrl);
                    break;

                case ExoPlayer.STATE_ENDED:
                    // aTalkApp.showToastMessage(R.string.gui_playback_completed);
                    break;

                case ExoPlayer.STATE_BUFFERING:
                case ExoPlayer.STATE_READY:
                default:
                    break;
            }
        }
    }
}

