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
import android.os.*;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class AudioBgService extends Service implements MediaPlayer.OnCompletionListener
{
    private static final String TAG = "AudioBgService";

    public static final String ACTION_RECORDING = "recording";
    public static final String ACTION_PLAYBACK = "playback";
    public static final String ACTION_CANCEL = "cancel";
    public static final String ACTION_SEND = "send";

    public static final String ACTION_AUDIO_RECORD = "audio_record";
    public static final String ACTION_SMI = "sound_meter_info";

    public static final String URI = "uri";
    public static final String SPL_LEVEL = "spl_level";
    public static final String RECORD_TIMER = "record_timer";

    private File audioFile = null;
    private String filePath = null;

    private MediaRecorder mRecorder = null;
    private MediaPlayer mPlayer = null;

    private long startTime = 0L;

    // Handler for Sound Level Meter and Record Timer
    private Handler mHandler = new Handler();

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

    @Nullable
    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        stopTimer();
        stopRecording();
        stopPlayBack();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        super.onStartCommand(intent, flags, startId);
        Bundle bundle = intent.getExtras();

        switch (intent.getAction()) {
            case ACTION_RECORDING:
                recordAudio();
                break;

            case ACTION_PLAYBACK:
                filePath = bundle.getString(URI);
                playAudio(filePath);
                break;

            case ACTION_SEND:
                stopTimer();
                stopRecording();
                if (audioFile != null) {
                    filePath = audioFile.getAbsolutePath();
                    sendBroadcast(filePath);
                }
                break;

            case ACTION_CANCEL:
                stopTimer();
                stopRecording();
                if (audioFile != null) {
                    File soundFile = new File(audioFile.getAbsolutePath());
                    soundFile.delete();
                    soundFile = null;
                    audioFile = null;
                }
                stopSelf();
                break;
        }
        return START_NOT_STICKY;
    }

    public void recordAudio()
    {
        try {
            File sampleDir = getOutputMediaFolder();
            audioFile = File.createTempFile("voice-", ".3gp", sampleDir);
        } catch (IOException e) {
            Log.e(TAG, "sdcard access error");
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
            Log.e(TAG, "io problems while recording [" + audioFile.getAbsolutePath() + "]: " + e.getMessage());
        }

        startTime = SystemClock.uptimeMillis();
        mHandler.postDelayed(updateSPL, 0);
    }

    public void playAudio(String uri)
    {
        mPlayer = new MediaPlayer();
        mPlayer.setOnCompletionListener(this);
        mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            mPlayer.setDataSource(uri);
            mPlayer.prepare();
            mPlayer.start();
        } catch (IOException e) {
            Log.e(TAG, "Incorrect audio file format: %s!".replace("%s", uri));
            e.printStackTrace();
            stopSelf();
        }
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
                /**
                 * Note that a RuntimeException is intentionally thrown to the application, if no
                 * valid audio/video data has been received when stop() is called. This happens
                 * if stop() is called immediately after start().
                 */
                ex.printStackTrace();
            }
        }
    }

    private void stopPlayBack()
    {
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
    }

    private void stopTimer()
    {
        if (mHandler != null) {
            mHandler.removeCallbacks(updateSPL);
            mHandler = null;
        }
    }

    // Listener for playback completion
    @Override
    public void onCompletion(MediaPlayer mp)
    {
        mp.release();
        mPlayer = null;
        stopSelf();
    }

    private void sendBroadcast(String filePath)
    {
        Intent intent = new Intent(ACTION_AUDIO_RECORD);
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
            mHandler.postDelayed(this, 100);
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

    private static File getOutputMediaFolder()
            throws IOException
    {
        String state = Environment.getExternalStorageState();
        if (!state.equals(Environment.MEDIA_MOUNTED)) {
            throw new IOException("SD Card is not mounted. It is %s .".replace("%s", state));
        }

        File mediaStorageDir
                = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Sound");

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs()) {
            Log.d("MySoundApp", "Path to voice file could not be created.");
            return null;
        }
        return mediaStorageDir;
    }
}
