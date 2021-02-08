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
package org.atalk.android.plugin.video;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.util.MimeTypes;

import org.apache.http.util.TextUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.persistance.FileBackend;

import java.util.ArrayList;
import java.util.List;

import at.huber.youtubeExtractor.VideoMeta;
import at.huber.youtubeExtractor.YouTubeExtractor;
import at.huber.youtubeExtractor.YtFile;
import timber.log.Timber;

/**
 * The class handles the actual content source address decoding for the user selected hymn
 * see https://developer.android.com/codelabs/exoplayer-intro#0
 *
 * @author Eng Chong Meng
 */
public class MediaExoPlayer extends FragmentActivity
{
    // Tag for the instance state bundle.
    public static final String ATTR_MEDIA_URL = "mediaUrl";
    public static final String ATTR_MEDIA_URLS = "mediaUrls";
    private static final String START_POSITION = "start_position";

    private static final String sampleUrl = "https://www.learningcontainer.com/wp-content/uploads/2020/05/sample-mp4-file.mp4";

    // Start playback video url
    private String mediaUrl = sampleUrl;
    private ArrayList<String> mediaUrls = null;

    // Playback position (in milliseconds).
    private long startPositionMs = 0;

    private SimpleExoPlayer mExoPlayer = null;
    private StyledPlayerView mPlayerView;
    private PlaybackStateListener playbackStateListener;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.media_player_exo_ui);
        mPlayerView = findViewById(R.id.exoplayerView);

        if (savedInstanceState != null) {
            mediaUrl = savedInstanceState.getString(ATTR_MEDIA_URL);
            mediaUrls = savedInstanceState.getStringArrayList(ATTR_MEDIA_URLS);
            startPositionMs = savedInstanceState.getLong(START_POSITION);
        }
        else {
            Bundle bundle = getIntent().getExtras();
            mediaUrl = bundle.getString(ATTR_MEDIA_URL);
            mediaUrls = bundle.getStringArrayList(ATTR_MEDIA_URLS);
        }
        playbackStateListener = new PlaybackStateListener();
    }

    /**
     * Save a copy of the current player info to the instance state bundle for:
     * a. Playback video url
     * b. Current playback position using getCurrentPosition (in milliseconds).
     *
     * if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
     *   onSaveInstanceState is called after onPause but before onStop
     * if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
     *   onSaveInstanceState is called only after onStop
     * So call releasePlayer() in onPause to save startPositionMs = mExoPlayer.getCurrentPosition();
     *
     * @param outState Bundle
     */
    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);

        outState.putString(ATTR_MEDIA_URL, mediaUrl);
        outState.putStringArrayList(ATTR_MEDIA_URLS, mediaUrls);
        outState.putLong(START_POSITION, startPositionMs);
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        hideSystemUi();
        // Load the media each time onStart() is called.
        initializePlayer();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        releasePlayer();
    }

    private void initializePlayer()
    {
        if (mExoPlayer == null) {
            mExoPlayer = new SimpleExoPlayer.Builder(this).build();
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
     * Prepare and play the specified mediaItem
     *
     * @param mediaItem for playback
     */
    private void playMedia(MediaItem mediaItem)
    {
        if (mediaItem != null) {
            mExoPlayer.setMediaItem(mediaItem, startPositionMs);
            mExoPlayer.setPlayWhenReady(true);
            mExoPlayer.prepare();
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
            mExoPlayer.setMediaItems(mediaItems);
            mExoPlayer.setPlayWhenReady(true);
            mExoPlayer.prepare();
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
        MediaItem mediaItem = null;

        Uri uri = Uri.parse(mediaUrl);
        String mimeType = FileBackend.getMimeType(this, uri);
        if (!TextUtils.isEmpty(mimeType) && (mimeType.contains("video") || mimeType.contains("audio"))) {
            mediaItem = MediaItem.fromUri(mediaUrl);
        }
        else if (mediaUrl.matches("http[s]*://[w.]*youtu[.]*be.*")) {
            playYoutubeUrl(mediaUrl);
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
     * see https://github.com/HaarigerHarald/android-youtubeExtractor
     * see https://github.com/flagbug/YoutubeExtractor
     *
     * @param youtubeLink the given youtube playback link
     */
    @SuppressLint("StaticFieldLeak")
    private void playYoutubeUrl(String youtubeLink)
    {
        try {
            new YouTubeExtractor(this)
            {
                @Override
                public void onExtractionComplete(SparseArray<YtFile> ytFiles, VideoMeta vMeta)
                {
                    if (ytFiles != null) {
                        int itag = ytFiles.keyAt(0); //22; get the first available itag
                        String downloadUrl = ytFiles.get(itag).getUrl();
                        MediaItem mediaItem = MediaItem.fromUri(downloadUrl);
                        playMedia(mediaItem);
                    }
                    else {
                        aTalkApp.showToastMessage(R.string.gui_playback_error);
                        playVideoUrlExt(youtubeLink);
                    }
                }
            }.extract(youtubeLink, true, true);
        } catch (Exception e) {
            Timber.e("YouTubeExtractor Exception: %s", e.getMessage());
        }
    }

    /**
     * Media play-back takes a lot of resources, so everything should be stopped and released at this time.
     * Release all media-related resources. In a more complicated app this
     * might involve unregistering listeners or releasing audio focus.
     */
    private void releasePlayer()
    {
        if (mExoPlayer != null) {
            startPositionMs = mExoPlayer.getCurrentPosition();
            mExoPlayer.setPlayWhenReady(false);
            mExoPlayer.removeListener(playbackStateListener);
            mExoPlayer.release();
            mExoPlayer = null;
        }
    }

    /**
     * playback in full screen
     */
    private void hideSystemUi()
    {
        mPlayerView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    /**
     * ExoPlayer playback state listener
     */
    private static class PlaybackStateListener implements Player.EventListener
    {
        @Override
        public void onPlaybackStateChanged(int playbackState)
        {
            switch (playbackState) {
                case ExoPlayer.STATE_IDLE:
                    aTalkApp.showToastMessage(R.string.gui_playback_error);
                    break;

                case ExoPlayer.STATE_BUFFERING:
                    // aTalkApp.showToastMessage(R.string.gui_playback_buffering);
                    break;

                case ExoPlayer.STATE_READY:
                    break;

                case ExoPlayer.STATE_ENDED:
                    // aTalkApp.showToastMessage(R.string.gui_playback_completed);
                    break;

                default:
                    break;
            }
        }
    }

    /**
     * Play the specified videoUrl using android Intent.ACTION_VIEW
     *
     * @param videoUrl videoUrl not playable by ExoPlayer
     */
    private void playVideoUrlExt(String videoUrl)
    {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}

