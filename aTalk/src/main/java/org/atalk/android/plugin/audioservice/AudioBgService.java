/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
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
package org.atalk.android.plugin.audioservice;

import android.app.Service;
import android.content.Intent;
import android.media.*;
import android.net.Uri;
import android.os.*;

import org.atalk.persistance.FileBackend;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import timber.log.Timber;

public class AudioBgService extends Service implements MediaPlayer.OnCompletionListener
{
    // Media player actions
    public static final String ACTION_PLAYER_INIT = "player_init";
    public static final String ACTION_PLAYER_START = "player_start";
    public static final String ACTION_PLAYER_PAUSE = "player_pause";
    public static final String ACTION_PLAYER_STOP = "player_stop";
    public static final String ACTION_PLAYER_SEEK = "player_seek";

    // Playback without any UI update
    public static final String ACTION_PLAYBACK_PLAY = "playback_play";

    // Media player broadcast status parameters
    public static final String PLAYBACK_STATE = "playback_state";
    public static final String PLAYBACK_STATUS = "playback_status";
    public static final String PLAYBACK_DURATION = "playback_duration";
    public static final String PLAYBACK_POSITION = "playback_position";
    public static final String PLAYBACK_URI = "playback_uri";

    // Handler for media player playback status broadcast
    private Handler mHandlerPlayback;

    private MediaPlayer mPlayer = null;
    private Uri fileUri;

    public enum PlaybackState
    {
        init,
        play,
        pause,
        stop
    }

    // Audio recording
    public static final String ACTION_RECORDING = "recording";
    public static final String ACTION_CANCEL = "cancel";
    public static final String ACTION_SEND = "send";

    public static final String ACTION_AUDIO_RECORD = "audio_record";
    public static final String ACTION_SMI = "sound_meter_info";

    public static final String URI = "uri";
    public static final String SPL_LEVEL = "spl_level";
    public static final String RECORD_TIMER = "record_timer";

    private File audioFile = null;

    private MediaRecorder mRecorder = null;

    private Map<Uri, MediaPlayer> uriPlayers = new ConcurrentHashMap<>();

    private long startTime = 0L;

    // Handler for Sound Level Meter and Record Timer
    private Handler mHandlerRecord;

    // The Google ASR input requirements state that audio input sensitivity should be set such
    // that 90 dB SPL_LEVEL at 1000 Hz yields RMS of 2500 for 16-bit samples,
    // i.e. 20 * log_10 (2500 / mGain) = 90.
    private double mGain = 2500.0 / Math.pow(10.0, 90.0 / 20.0);

    // For displaying error in calibration.
    public static double mOffsetDB = 0.0f;  //10 Offset for bar, i.e. 0 lit LEDs at 10 dB.
    public static double mDBRange = 70.0f;  //SPL display range.

    private static double mEMA = 1.0; // Temporally filtered version of RMS
    //private double mAlpha =  0.9 Coefficient of IIR smoothing filter for RMS.
    static final private double EMA_FILTER = 0.4;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        super.onStartCommand(intent, flags, startId);
        switch (intent.getAction()) {
            case ACTION_PLAYER_INIT:
                fileUri = intent.getData();
                playerInit(fileUri);
                break;

            case ACTION_PLAYER_START:
                fileUri = intent.getData();
                playerStart(fileUri);
                break;

            case ACTION_PLAYER_PAUSE:
                fileUri = intent.getData();
                playerPause(fileUri);
                break;

            case ACTION_PLAYER_STOP:
                fileUri = intent.getData();
                playerRelease(fileUri);
                break;

            case ACTION_PLAYER_SEEK:
                fileUri = intent.getData();
                int seekPosition = intent.getIntExtra(AudioBgService.PLAYBACK_POSITION, 0);
                playerSeek(fileUri, seekPosition);
                break;

            case ACTION_PLAYBACK_PLAY:
                fileUri = intent.getData();
                playerPlay(fileUri);
                break;

            case ACTION_RECORDING:
                mHandlerRecord = new Handler();
                recordAudio();
                break;

            case ACTION_SEND:
                stopTimer();
                stopRecording();
                if (audioFile != null) {
                    // sendBroadcast(FileAccess.getUriForFile(this, audioFile));
                    String filePath = audioFile.getAbsolutePath();
                    sendBroadcast(filePath);
                }
                break;

            case ACTION_CANCEL:
                stopTimer();
                stopRecording();
                if (audioFile != null) {
                    File soundFile = new File(audioFile.getAbsolutePath());
                    soundFile.delete();
                    audioFile = null;
                }
                stopSelf();
                break;
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        stopTimer();
        stopRecording();

        if (mHandlerPlayback != null) {
            mHandlerPlayback.removeCallbacks(playbackStatus);
            mHandlerPlayback = null;
        }

        for (Uri uri : uriPlayers.keySet()) {
            fileUri = uri;
            playerRelease(uri);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    /* =============================================================
     * Media player handlers
     * ============================================================= */

    /**
     * Create a new media player instance for the specified uri
     *
     * @param uri Media file uri
     * @return true is creation is successful
     */
    public boolean playerCreate(Uri uri)
    {
        if (uri == null)
            return false;

        if (mHandlerPlayback == null)
            mHandlerPlayback = new Handler();

        mPlayer = new MediaPlayer();
        uriPlayers.put(uri, mPlayer);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mPlayer.setAudioAttributes(
                    new AudioAttributes.Builder().setLegacyStreamType(AudioManager.STREAM_MUSIC).build());
        }
        else {
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        }

        try {
            mPlayer.setOnCompletionListener(this);
            mPlayer.setDataSource(this, uri);
            mPlayer.prepare();
        } catch (IOException e) {
            Timber.e("Media player creation error for: %s", uri.getPath());
            playerRelease(uri);
            return false;
        }
        return true;
    }

    /**
     * Return the status of current active player if present; keep the state as it
     * else get the media file info and release player to conserve resource
     *
     * @param uri Media file uri
     */
    public void playerInit(Uri uri)
    {
        if (uri == null)
            return;

        if (mHandlerPlayback == null)
            mHandlerPlayback = new Handler();

        // Check player status on return to chatSession before start new
        mPlayer = uriPlayers.get(uri);
        if (mPlayer != null) {
            if (mPlayer.isPlaying()) {
                playbackState(PlaybackState.play, uri);
                // Cancel and resync with only one loop running
                mHandlerPlayback.removeCallbacks(playbackStatus);
                mHandlerPlayback.postDelayed(playbackStatus, 500);
            }
            else {
                int position = mPlayer.getCurrentPosition();
                int duration = mPlayer.getDuration();
                if ((position > 0) && (position <= duration)) {
                    playbackState(PlaybackState.pause, uri);
                }
                else {
                    playerReInit(uri);
                }
            }
        }
        else {
            // Create new to get media info and then release to conserve resource
            if (playerCreate(uri)) {
                playerRelease(uri);
            }
        }
    }

    /**
     * Reinit an existing player and return its status
     *
     * @param uri Media file uri
     */
    private void playerReInit(Uri uri)
    {
        mPlayer = uriPlayers.get(uri);
        if (mPlayer != null) {
            mPlayer.seekTo(0);
            if (mPlayer.isPlaying())
                mPlayer.pause();
            playbackState(PlaybackState.init, uri);
        }
    }

    /**
     * Pause the current player and return the action result
     *
     * @param uri Media file uri
     */
    public void playerPause(Uri uri)
    {
        if (uri == null)
            return;

        mPlayer = uriPlayers.get(fileUri);
        if (mPlayer == null) {
            playbackState(PlaybackState.stop, uri);
        }
        else if (mPlayer.isPlaying()) {
            mPlayer.pause();
            playbackState(PlaybackState.pause, uri);
        }
    }

    /**
     * Start playing back on existing player or create new if none
     * Broadcast the player satus at regular interval
     *
     * @param uri Media file uri
     */
    public void playerStart(Uri uri)
    {
        if (uri == null)
            return;

        mPlayer = uriPlayers.get(uri);
        if (mPlayer == null) {
            playerCreate(uri);
        }
        else if (mPlayer.isPlaying()) {
            return;
        }

        try {
            mPlayer.start();
            playbackState(PlaybackState.play, uri);
        } catch (Exception e) {
            Timber.e("Playback failed");
            playerRelease(uri);
        }
        mHandlerPlayback.removeCallbacks(playbackStatus);
        mHandlerPlayback.postDelayed(playbackStatus, 500);
    }

    /**
     * Start playing back on existing player or create new if none
     * Broadcast the player satus at regular interval
     *
     * @param uri Media file uri
     */
    public void playerSeek(Uri uri, int seekPosition)
    {
        if (uri == null)
            return;

        mPlayer = uriPlayers.get(uri);
        if ((mPlayer == null) && !playerCreate(uri))
            return;

        try {
            mPlayer.seekTo(seekPosition);
            if (!mPlayer.isPlaying())
                playbackState(PlaybackState.pause, uri);
        } catch (Exception e) {
            Timber.e("Playback failed");
            playerRelease(uri);
        }
    }

    /**
     * Release the player resource and remove it from uriPlayers
     *
     * @param uri Media file uri
     */
    private void playerRelease(Uri uri)
    {
        mPlayer = uriPlayers.get(uri);
        if (mPlayer != null) {
            mPlayer.seekTo(0);
            playbackState(PlaybackState.stop, uri);
            uriPlayers.remove(uri);

            if (mPlayer.isPlaying()) {
                mPlayer.stop();
            }
            mPlayer.reset();
            mPlayer.release();
            mPlayer = null;
        }
    }

    // Listener for playback completion

    /**
     * callback from media player when playback of a media source has completed.
     *
     * @param mp Media Player instance
     */
    @Override
    public void onCompletion(MediaPlayer mp)
    {
        fileUri = getUriByPlayer(mp);
        if (fileUri == null) {
            mp.release();
            stopSelf();
        }
        else {
            playerRelease(fileUri);
        }
    }

    /**
     * Return the uri of the given mp
     *
     * @param mp the media player
     * @return Uri of the player
     */
    private Uri getUriByPlayer(MediaPlayer mp)
    {
        for (Map.Entry<Uri, MediaPlayer> entry : uriPlayers.entrySet()) {
            if (entry.getValue().equals(mp)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Broadcast the relevant info of the media player (uri)
     * a. player state
     * b. player uri file
     * c. playback position
     * d. uri playback duration
     *
     * @param pState player state
     * @param uri media file uri
     */
    private void playbackState(PlaybackState pState, Uri uri)
    {
        MediaPlayer xPlayer = uriPlayers.get(uri);
        if (xPlayer != null) {
            Intent intent = new Intent(PLAYBACK_STATE);
            intent.putExtra(PLAYBACK_URI, uri);
            intent.putExtra(PLAYBACK_STATE, pState);
            intent.putExtra(PLAYBACK_POSITION, xPlayer.getCurrentPosition());
            intent.putExtra(PLAYBACK_DURATION, xPlayer.getDuration());

            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            // Timber.d("Audio playback state: %s (%s): %s", pState, xPlayer.getDuration(), uri.getPath());
        }
    }

    /**
     * Broadcast the relevant info of the media playback status (uri); loop@500ms until no active player
     * a. player uri file
     * b. playback position
     * c. uri playback duration
     */
    private Runnable playbackStatus = new Runnable()
    {
        public void run()
        {
            boolean hasActivePlayer = false;

            for (Map.Entry<Uri, MediaPlayer> entry : uriPlayers.entrySet()) {
                MediaPlayer playerX = entry.getValue();
                if ((playerX == null) || !playerX.isPlaying())
                    continue;

                hasActivePlayer = true;
                // Timber.d("Audio playback state: %s:  %s", playerX.getCurrentPosition(), entry.getKey());

                Intent intent = new Intent(PLAYBACK_STATUS);
                intent.putExtra(PLAYBACK_URI, entry.getKey());
                intent.putExtra(PLAYBACK_POSITION, playerX.getCurrentPosition());
                intent.putExtra(PLAYBACK_DURATION, playerX.getDuration());
                LocalBroadcastManager.getInstance(AudioBgService.this).sendBroadcast(intent);
            }

            if (hasActivePlayer)
                mHandlerPlayback.postDelayed(this, 500);
        }
    };

    /**
     * Playback media audio without any UI update
     * hence mHandlerPlayback not required
     *
     * @param uri the audio file
     */
    public void playerPlay(Uri uri)
    {
        if (playerCreate(uri)) {
            mPlayer.start();
            uriPlayers.remove(uri);
        }
        mHandlerPlayback = null;
    }

    /* =============================================================
     * Voice recording handlers
     * ============================================================= */
    public void recordAudio()
    {
        audioFile = createMediaVoiceFile();
        if (audioFile == null) {
            return;
        }

        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mRecorder.setOutputFile(audioFile.getAbsolutePath());

        try {
            mRecorder.prepare();
            mRecorder.start();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            Timber.e("io problems while recording [%s]: %s", audioFile.getAbsolutePath(), e.getMessage());
        }

        startTime = SystemClock.uptimeMillis();
        mHandlerRecord.postDelayed(updateSPL, 0);
    }

    private void stopRecording()
    {
        if (mRecorder != null) {
            try {
                mRecorder.stop();
                mRecorder.reset();
                mRecorder.release();
                mRecorder = null;
            } catch (RuntimeException ex) {
                /*
                 * Note that a RuntimeException is intentionally thrown to the application, if no
                 * valid audio/video data has been received when stop() is called. This happens
                 * if stop() is called immediately after start().
                 */
                ex.printStackTrace();
            }
        }
    }

    private void stopTimer()
    {
        if (mHandlerRecord != null) {
            mHandlerRecord.removeCallbacks(updateSPL);
            mHandlerRecord = null;
        }
    }

    private void sendBroadcast(String filePath)
    {
        Intent intent = new Intent(ACTION_AUDIO_RECORD);
        // intent.setDataAndType(uri, "video/3gp");
        intent.putExtra(URI, filePath);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private Runnable updateSPL = new Runnable()
    {
        public void run()
        {
            long finalTime = SystemClock.uptimeMillis() - startTime;
            int seconds = (int) (finalTime / 1000);
            int minutes = seconds / 60;
            seconds = seconds % 60;
            String mDuration = String.format(Locale.US, "%02d:%02d", minutes, seconds);

            double mRmsSmoothed = getAmplitudeEMA();
            final double rmsdB = 20.0 * Math.log10(mGain * mRmsSmoothed);

            // The bar has an input range of [0.0 ; 1.0] and 14 segments.
            // Each LED corresponds to 70/14 dB.
            double mSPL = (mOffsetDB + rmsdB) / mDBRange;
            // mBarLevel.setLevel(mSPL);

            Intent intent = new Intent(ACTION_SMI);
            intent.putExtra(SPL_LEVEL, mSPL);
            intent.putExtra(RECORD_TIMER, mDuration);
            LocalBroadcastManager.getInstance(AudioBgService.this).sendBroadcast(intent);
            mHandlerRecord.postDelayed(this, 100);
        }
    };

    public double getAmplitudeEMA()
    {
        double amp = getAmplitude();
        // Compute a smoothed version for less flickering of the display.
        mEMA = EMA_FILTER * mEMA + (1.0 - EMA_FILTER) * amp;
        return mEMA;
    }

    public double getAmplitude()
    {
        if (mRecorder != null)
            return (mRecorder.getMaxAmplitude());
        else
            return 0;
    }

    /**
     * Create the audio file if it does not exist
     *
     * @return Voice file for saving audio
     */
    private static File createMediaVoiceFile()
    {
        File voiceFile = null;
        File mediaDir = FileBackend.getaTalkStore(FileBackend.MEDIA_VOICE_SEND, true);
        if (!mediaDir.exists() && !mediaDir.mkdirs()) {
            Timber.d("Fail to create Media voice directory!");
            return null;
        }

        try {
            voiceFile = File.createTempFile("voice-", ".3gp", mediaDir);
        } catch (IOException e) {
            Timber.d("Fail to create Media voice file!");
        }
        return voiceFile;
    }
}
