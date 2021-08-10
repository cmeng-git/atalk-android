package org.atalk.android.plugin.textspeech;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;

import org.atalk.android.aTalkApp;

import java.util.*;

public class TTSService extends Service implements TextToSpeech.OnInitListener
{
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_QMODE = "qmode";

    private TextToSpeech mTTS;
    private boolean isInit;
    private Handler handler;

    private String message = "Text to speech is ready";
    private boolean qMode = true;

    @Override
    public void onCreate()
    {
        super.onCreate();
        handler = new Handler();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        handler.removeCallbacksAndMessages(null);
        message = intent.getStringExtra(TTSService.EXTRA_MESSAGE);
        qMode = intent.getBooleanExtra(TTSService.EXTRA_QMODE, true);

        if (mTTS == null) {
            mTTS = new TextToSpeech(getApplicationContext(), this);
        }
        else if (isInit) {
            speak(message, qMode);
        }

        // Hold the tts for 60 minutes min before release the resource
        handler.postDelayed(this::stopSelf, 60 * 60 * 1000);
        return TTSService.START_NOT_STICKY;
    }

    @Override
    public void onDestroy()
    {
        if (mTTS != null) {
            mTTS.stop();
            mTTS.shutdown();
        }
        super.onDestroy();
    }

    /**
     * Called to signal the completion of the TextToSpeech engine initialization.
     *
     * @param status {@link TextToSpeech#SUCCESS} or {@link TextToSpeech#ERROR}.
     */
    @Override
    public void onInit(int status)
    {
        if (status == TextToSpeech.SUCCESS) {
            if (mTTS != null) {
                Locale language = mTTS.getDefaultVoice().getLocale();
                if (language != null) {
                    int result = mTTS.setLanguage(language);
                    if ((result == TextToSpeech.LANG_MISSING_DATA) || (result == TextToSpeech.LANG_NOT_SUPPORTED)) {
                        aTalkApp.showToastMessage("TTS language is not supported");
                    }
                    else {
                        speak(message, qMode);
                        isInit = true;
                    }
                }
            }
            else {
                aTalkApp.showToastMessage("TTS initialization failed");
            }
        }
    }

    public void speak(String text, boolean qMode)
    {
        if ((mTTS != null) && !TextUtils.isEmpty(text)) {
            text = text.replaceAll("<.*?>", "");

            List<String> messages = splitEqually(text);
            qMode = (messages.size() > 1) || qMode;

            for (String segmentText : messages) {
                Bundle params = new Bundle();
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, segmentText);

                if (qMode)
                    mTTS.speak(segmentText, TextToSpeech.QUEUE_ADD, params, null);
                else
                    mTTS.speak(segmentText, TextToSpeech.QUEUE_FLUSH, params, null);
            }
        }
    }

    /**
     * Split the text to speak into List each of less than TextToSpeech.getMaxSpeechInputLength()
     *
     * @param text speak text string
     * @return Split text in List<String>
     */
    public static List<String> splitEqually(String text)
    {
        int size = TextToSpeech.getMaxSpeechInputLength() - 1;

        // Give the list the right capacity to start with.

        List<String> ret = new ArrayList<>((text.length() + size - 1) / size);
        for (int start = 0; start < text.length(); start += size) {
            ret.add(text.substring(start, Math.min(text.length(), start + size)));
        }
        return ret;
    }

    @Override
    public IBinder onBind(Intent arg0)
    {
        return null;
    }
}
