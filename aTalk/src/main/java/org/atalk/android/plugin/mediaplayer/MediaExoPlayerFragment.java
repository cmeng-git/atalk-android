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
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.List;

import net.java.sip.communicator.util.UtilActivator;

import org.apache.http.util.TextUtils;
import org.atalk.android.BaseFragment;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.persistance.FileBackend;
import org.atalk.service.configuration.ConfigurationService;

/**
 * The class handles the actual content source address decoding for the user selected hymn
 * see <a href="https://developer.android.com/codelabs/exoplayer-intro#0">...</a>
 * <p>
 * This MediaExoPlayerFragment requires its parent FragmentActivity to handle onConfigurationChanged()
 * It does not consider onSaveInstanceState(); it uses the speed in the user configuration setting.
 *
 * @author Eng Chong Meng
 */
public class MediaExoPlayerFragment extends BaseFragment {
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

    private static final ConfigurationService configService = UtilActivator.getConfigurationService();

    private ExoPlayer mExoPlayer = null;
    private PlayerView mPlayerView;
    private PlaybackStateListener playbackStateListener;

    /**
     * Create a new instance of MediaExoPlayerFragment, providing "bundle" as an argument.
     */
    public static MediaExoPlayerFragment getInstance(Bundle args) {
        MediaExoPlayerFragment exoPlayerFragment = new MediaExoPlayerFragment();
        exoPlayerFragment.setArguments(args);
        return exoPlayerFragment;
    }

    @SuppressLint("CommitPrefEdits")
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
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
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View mConvertView = inflater.inflate(R.layout.media_player_exo_ui, container, false);
        mPlayerView = mConvertView.findViewById(R.id.exoplayerView);

        if (container != null)
            container.setVisibility(View.VISIBLE);
        return mConvertView;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Load the media and start playback each time onResume() is called.
        initializePlayer();
    }

    @Override
    public void onPause() {
        super.onPause();
        releasePlayer();
    }

    public void initializePlayer() {
        if (mExoPlayer == null) {
            mExoPlayer = new ExoPlayer.Builder(mContext).build();
            mExoPlayer.addListener(playbackStateListener);
            mPlayerView.setPlayer(mExoPlayer);
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
     * Save the user defined playback speed
     */
    public void releasePlayer() {
        if (mExoPlayer != null) {
            mSpeed = mExoPlayer.getPlaybackParameters().speed;

            // Audio media player speed is (0.25 >= mSpeed <= 2.0)
            if (mSpeed >= rateMin && mSpeed <= rateMax) {
                configService.setProperty(PREF_PLAYBACK_SPEED, mSpeed);
            }

            mExoPlayer.setPlayWhenReady(false);
            mExoPlayer.removeListener(playbackStateListener);
            mExoPlayer.release();
            mExoPlayer = null;
        }
    }

    /**
     * Prepare and play the specified mediaItem
     *
     * @param mediaItem for playback
     */
    private void playMedia(MediaItem mediaItem) {
        if (mediaItem != null) {
            mSpeed = (float) configService.getDouble(PREF_PLAYBACK_SPEED, 1.0f);
            setPlaybackSpeed(mSpeed);

            mExoPlayer.setMediaItem(mediaItem, 0);
            mExoPlayer.setPlayWhenReady(true);
            mExoPlayer.prepare();
        }
    }

    /**
     * Prepare and playback a list of given video URLs if not empty
     */
    private void playVideoUrls() {
        if ((mediaUrls != null) && !mediaUrls.isEmpty()) {
            List<MediaItem> mediaItems = new ArrayList<>();
            for (String tmpUrl : mediaUrls) {
                mediaItems.add(buildMediaItem(tmpUrl));
            }

            mSpeed = (float) configService.getDouble(PREF_PLAYBACK_SPEED, 1.0);
            setPlaybackSpeed(mSpeed);

            mExoPlayer.setMediaItems(mediaItems);
            mExoPlayer.setPlayWhenReady(true);
            mExoPlayer.prepare();
        }
    }

    /**
     * Play the specified videoUrl using android Intent.ACTION_VIEW
     * Use setDataAndType(uri, mimeType) to ensure android has default defined.
     *
     * @param videoUrl videoUrl not playable by ExoPlayer
     */
    private void playVideoUrlExt(String videoUrl) {
        // remove the exoPlayer fragment
        mFragmentActivity.getSupportFragmentManager().beginTransaction().remove(this).commit();
        Uri uri = Uri.parse(videoUrl);
        String mimeType = FileBackend.getMimeType(mContext, uri);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    /**
     * set SimpleExoPlayer playback speed
     *
     * @param speed playback speed: default 1.0f
     */
    private void setPlaybackSpeed(float speed) {
        PlaybackParameters playbackParameters = new PlaybackParameters(speed, 1.0f);
        if (mExoPlayer != null) {
            mExoPlayer.setPlaybackParameters(playbackParameters);
        }
    }

    /**
     * Build and return the mediaItem or
     * Proceed to play if it is a youtube link; return null;
     *
     * @param mediaUrl for building the mediaItem
     *
     * @return built mediaItem
     */
    private MediaItem buildMediaItem(String mediaUrl) {
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
    private class PlaybackStateListener implements Player.Listener {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            switch (playbackState) {
                case ExoPlayer.STATE_IDLE:
                    aTalkApp.showToastMessage(R.string.playback_error);
                    // Attempt to use android player if exoplayer failed to play
                    playVideoUrlExt(mediaUrl);
                    break;

                case ExoPlayer.STATE_ENDED:
                    // aTalkApp.showToastMessage(R.string.gui_playback_completed);
                    break;

                case ExoPlayer.STATE_READY:
                    if (VideoSize.UNKNOWN.equals(mExoPlayer.getVideoSize())) {
                        float vHeight = 0.62f * aTalkApp.mDisplaySize.width;
                        mPlayerView.setLayoutParams(new LinearLayout.LayoutParams(aTalkApp.mDisplaySize.width, (int) vHeight));
                    }
                    break;

                case ExoPlayer.STATE_BUFFERING:
                default:
                    break;
            }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            Throwable cause = error.getCause();
            String msg = cause != null ? cause.getMessage() : getString(R.string.playback_error);
            aTalkApp.showToastMessage(msg);
        }
    }

    public void setPlayerVisible(boolean show) {
        mPlayerView.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    public boolean isPlayerVisible() {
        return mPlayerView.getVisibility() == View.VISIBLE;
    }
}

