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
import android.os.Build;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;
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

// import java.lang.reflect.Field;

/**
 * The class handles the actual content source address decoding for the user selected hymn
 * see https://developer.android.com/codelabs/exoplayer-intro#0
 *
 * @author Eng Chong Meng
 */
public class MediaExoPlayer extends FragmentActivity
{
    // Tag for the instance state bundle.
    public static final String ATTR_VIDEO_URL = "videoUrl";
    public static final String ATTR_VIDEO_URLS = "videoUrls";

    private static final String START_POSITION = "start_position";

    private static final String sampleUrl = "https://www.learningcontainer.com/wp-content/uploads/2020/05/sample-mp4-file.mp4";

    // Start playback video url
    private String videoUrl = sampleUrl;
    private ArrayList<String> videoUrls = null;

    // Playback position (in milliseconds).
    private long startPositionMs = 0;

    private SimpleExoPlayer mVideoPlayer = null;
    private PlayerView mVideoView;
    private PlaybackStateListener playbackStateListener;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.media_player_exo_ui);
        mVideoView = findViewById(R.id.video_player_view);

        if (savedInstanceState != null) {
            videoUrl = savedInstanceState.getString(ATTR_VIDEO_URL);
            videoUrls = savedInstanceState.getStringArrayList(ATTR_VIDEO_URLS);
            startPositionMs = savedInstanceState.getLong(START_POSITION);
        }
        else {
            Bundle bundle = getIntent().getExtras();
            videoUrl = bundle.getString(ATTR_VIDEO_URL);
            videoUrls = bundle.getStringArrayList(ATTR_VIDEO_URLS);
        }
        playbackStateListener = new PlaybackStateListener();
    }

    /**
     * Save a copy of the current player info to the instance state bundle for:
     * a. Playback video url
     * b. Current playback position using getCurrentPosition (in milliseconds).
     *
     * @param outState Bundle
     */
    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);

        outState.putString(ATTR_VIDEO_URL, videoUrl);
        outState.putStringArrayList(ATTR_VIDEO_URLS, videoUrls);
        outState.putLong(START_POSITION, mVideoPlayer.getCurrentPosition());
    }

    /*
     * Android API level 24 and higher supports multiple windows. As your app can be visible,
     * but not active in split window mode, you need to initialize the player in onStart.
     * Android API level 24 and lower requires you to wait as long as possible until you grab resources,
     * so you wait until onResume before initializing the player.
     */
    @Override
    protected void onStart()
    {
        super.onStart();

        // Load the media each time onStart() is called.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            initializePlayer();
        }
    }

    @Override
    protected void onResume()
    {
        hideSystemUi();
        super.onResume();
        // Load the media each time onStart() is called.
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.N) || (mVideoPlayer == null)) {
            initializePlayer();
        }
    }

    // With API Level lower than 24, there is no guarantee of onStop being called,
    // so you have to release the player as early as possible in onPause.
    @Override
    protected void onPause()
    {
        super.onPause();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            releasePlayer();
        }
    }

    @Override
    protected void onStop()
    {
        super.onStop();

        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) && isFinishing()) {
            releasePlayer();
        }
    }

    private void initializePlayer()
    {
        if (mVideoPlayer == null) {
            mVideoPlayer = new SimpleExoPlayer.Builder(this).build();
            mVideoPlayer.addListener(playbackStateListener);
            mVideoView.setPlayer(mVideoPlayer);
        }

        if ((videoUrls == null) || videoUrls.isEmpty()) {
            MediaItem mediaItem = buildMediaItem(videoUrl);
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
            mVideoPlayer.setMediaItem(mediaItem, startPositionMs);
            mVideoPlayer.setPlayWhenReady(true);
            mVideoPlayer.prepare();
        }
    }

    /**
     * Prepare and playback a list of given video URLs if not empty
     */
    private void playVideoUrls()
    {
        if ((videoUrls != null) && !videoUrls.isEmpty()) {
            List<MediaItem> mediaItems = new ArrayList<>();
            for (String tmpUrl : videoUrls) {
                mediaItems.add(buildMediaItem(tmpUrl));
            }
            mVideoPlayer.setMediaItems(mediaItems);
            mVideoPlayer.setPlayWhenReady(true);
            mVideoPlayer.prepare();
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
        if (!TextUtils.isEmpty(mimeType) && mimeType.contains("video")) {
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
        //        try {
        //            Field field = YouTubeExtractor.class.getDeclaredField("LOGGING");
        //            field.setAccessible(true);
        //            field.set(field, true);
        //        } catch (NoSuchFieldException | IllegalAccessException e) {
        //            Timber.w("Exception: %s", e.getMessage());
        //        }

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
        if (mVideoPlayer != null) {
            mVideoPlayer.setPlayWhenReady(false);
            mVideoPlayer.removeListener(playbackStateListener);
            mVideoPlayer.release();
            mVideoPlayer = null;
        }
    }

    /**
     * playback in full screen
     */
    private void hideSystemUi()
    {
        mVideoView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE
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
            String stateString;
            switch (playbackState) {
                case ExoPlayer.STATE_IDLE:
                    aTalkApp.showToastMessage(R.string.gui_playback_error);
                    stateString = "ExoPlayer.STATE_IDLE      -";
                    break;
                case ExoPlayer.STATE_BUFFERING:
                    aTalkApp.showToastMessage(R.string.gui_playback_buffering);
                    stateString = "ExoPlayer.STATE_BUFFERING -";
                    break;
                case ExoPlayer.STATE_READY:
                    stateString = "ExoPlayer.STATE_READY     -";
                    break;
                case ExoPlayer.STATE_ENDED:
                    // aTalkApp.showToastMessage(R.string.gui_playback_completed);
                    stateString = "ExoPlayer.STATE_ENDED     -";
                    break;
                default:
                    stateString = "UNKNOWN_STATE             -";
                    break;
            }
            Timber.d("ExoPlayer changes state to: %s", stateString);
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

