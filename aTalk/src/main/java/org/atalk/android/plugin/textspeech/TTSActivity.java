package org.atalk.android.plugin.textspeech;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.tts.*;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;

import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.persistance.FileBackend;
import org.atalk.service.osgi.OSGiActivity;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import timber.log.Timber;

public class TTSActivity extends OSGiActivity implements TextToSpeech.OnInitListener, View.OnClickListener,
        CompoundButton.OnCheckedChangeListener
{
    private static final String ACTION_TTS_SETTINGS = "com.android.settings.TTS_SETTINGS";

    private static final int ACT_CHECK_TTS_DATA = 1001;
    private static final int REQUEST_DEFAULT = 1003;

    private final int REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE = 2000;

    private int permissionCount = 0;
    private final String mUtteranceID = "totts";
    private String ttsDelay;

    private TextView mTtsText;
    private TextView mTtsLocale;
    private EditText mTtsDelay;
    Button btnPlay;
    Button btnSave;
    CheckBox cbTts;

    private TextToSpeech mTTS;
    private static State mState = State.UNKNOWN;

    public enum State
    {
        LOADING,
        DOWNLOAD_FAILED,
        ERROR,
        SUCCESS,
        UNKNOWN
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tts_main);
        mTtsText = findViewById(R.id.tts_text);
        mTtsText.addTextChangedListener(mTextWatcher);

        cbTts = findViewById(R.id.tts_enable);
        cbTts.setChecked(ConfigurationUtils.isTtsEnable());
        cbTts.setOnCheckedChangeListener(this);

        mTtsLocale = findViewById(R.id.tts_locale);
        mTtsDelay = findViewById(R.id.tts_delay);
        ttsDelay = String.valueOf(ConfigurationUtils.getTtsDelay());
        mTtsDelay.setText(ttsDelay);
        mTtsDelay.addTextChangedListener(mTextWatcher);

        Button btnSetting = findViewById(R.id.tts_setting);
        btnSetting.setOnClickListener(this);

        btnPlay = findViewById(R.id.tts_play);
        btnPlay.setOnClickListener(this);
        btnPlay.setEnabled(false);

        btnSave = findViewById(R.id.tts_save);
        btnSave.setOnClickListener(this);
        btnSave.setEnabled(false);

        Button btnOK = findViewById(R.id.tts_ok);
        btnOK.setOnClickListener(this);

        initButton();

        // Perform the dynamic permission request
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE);

        setState(State.LOADING);

        // Device without TTS engine will cause aTalk to crash.
        try {
            checkVoiceData();
        } catch (ActivityNotFoundException ex) {
            aTalkApp.showToastMessage(ex.getMessage());
            finish();
        }
    }

    @Override
    protected void onStop()
    {
        super.onStop();
        String tmp = ViewUtil.toString(mTtsDelay);
        if ((tmp != null) && !ttsDelay.equals(tmp)) {
            ConfigurationUtils.setTtsDelay(Integer.parseInt(tmp));
        }
    }

    private Locale getTtsLanguage()
    {
        if (mTTS != null) {
            try {
                Voice voice = mTTS.getVoice();
                if (voice != null) {
                    return voice.getLocale();
                }
            } catch (Exception e) {
                cbTts.setEnabled(false);
                String errMsg = "TTS get voice exception: " + e.getMessage();
                mTtsLocale.setText(errMsg);
                Timber.e(errMsg);
            }
        }
        return null;
    }

    /**
     * Handles the result of TTS engine initialization. Either displays an error
     * dialog or populates the activity's UI.
     *
     * @param status The TTS engine initialization status.
     */
    @Override
    public void onInit(int status)
    {
        if (status == TextToSpeech.SUCCESS) {
            setState(State.SUCCESS);

            Locale language = getTtsLanguage();
            if (language != null) {
                mTtsLocale.setText(language.getDisplayName());
                int result = mTTS.setLanguage(language);
                if ((result == TextToSpeech.LANG_MISSING_DATA) || (result == TextToSpeech.LANG_NOT_SUPPORTED)) {
                    aTalkApp.showToastMessage("TTS language is not supported");
                    setState(State.ERROR);
                }
                else {
                    Timber.i("Text to Speech is ready with status: %s", status);
                    // mTtsText.setText("Text to Speech is ready");
                    // btnPlay.performClick();
                }
            }
            else {
                setState(State.ERROR);
            }
        }
        else {
            setState(State.ERROR);
            Timber.e("Initialization failed (status: %s)", status);
        }
    }

    @Override
    public void onClick(View v)
    {
        String ttsText = ViewUtil.toString(mTtsText);
        switch (v.getId()) {
            case R.id.tts_setting:
                Intent intent = new Intent(ACTION_TTS_SETTINGS);
                startActivityForResult(intent, REQUEST_DEFAULT);
                break;

            case R.id.tts_play:
                if (ttsText != null) {
                    Intent spkIntent = new Intent(this, TTSService.class);
                    spkIntent.putExtra(TTSService.EXTRA_MESSAGE, ttsText);
                    spkIntent.putExtra(TTSService.EXTRA_QMODE, false);
                    startService(spkIntent);
                }
                break;

            case R.id.tts_save:
                if (ttsText != null)
                    saveToAudioFile(ttsText);
                break;

            case R.id.tts_ok:
                finish();
                break;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
    {
        switch (buttonView.getId()) {
            case R.id.tts_enable:
                ConfigurationUtils.setTtsEnable(isChecked);
                break;
        }
    }

    /**
     * Check to see if we have TTS voice data
     * Launcher the voice data verifier.
     */
    private void checkVoiceData()
    {
        final Intent checkIntent = new Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, ACT_CHECK_TTS_DATA);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch (requestCode) {
            case ACT_CHECK_TTS_DATA:
                onDataChecked(resultCode, data);
                break;
            case REQUEST_DEFAULT:
                initializeEngine();
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Handles the result of voice data verification. If verification fails
     * following a successful installation, displays an error dialog. Otherwise,
     * either launches the installer or attempts to initialize the TTS engine.
     *
     * @param resultCode The result of voice data verification.
     * @param data The intent containing available voices.
     */
    private void onDataChecked(int resultCode, Intent data)
    {
        if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
//            Intent intent = new Intent(this, TTSService.class);
//            startService(intent);
            // Data exists, so we instantiate the TTS engine
            initializeEngine();
        }
        else {
            Timber.e("Voice data check failed (error code: %s", resultCode);
            setState(State.ERROR);

            // Data is missing, so we start the TTS installation process
            Intent installIntent = new Intent();
            installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
            startActivity(installIntent);
        }
    }

    /**
     * Initializes the TTS engine.
     */
    private void initializeEngine()
    {
        if (mTTS != null)
            mTTS = null;
        mTTS = new TextToSpeech(this, this);
    }

    /**
     * Sets the UI state.
     *
     * @param state The current state.
     */
    private void setState(State state)
    {
        mState = state;
        switch (mState) {
            case LOADING:
                findViewById(R.id.loading).setVisibility(View.VISIBLE);
                findViewById(R.id.success).setVisibility(View.GONE);
                break;
            default:
                findViewById(R.id.loading).setVisibility(View.GONE);
                findViewById(R.id.success).setVisibility(View.VISIBLE);
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    permissionCount++;
            default:
                break;
        }
    }

    TextWatcher mTextWatcher = new TextWatcher()
    {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after)
        {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count)
        {
            initButton();
        }

        @Override
        public void afterTextChanged(Editable s)
        {
            initButton();
        }
    };

    private void initButton()
    {
        boolean enable = (State.SUCCESS == mState) && (mTtsText.getText().length() > 0);
        btnPlay.setEnabled(enable);
        btnSave.setEnabled(enable);

        float alpha = enable ? 1.0f : 0.5f;
        btnPlay.setAlpha(alpha);
        btnSave.setAlpha(alpha);
    }

    private void saveToAudioFile(String text)
    {
        // Create tts audio file
        File ttsFile = createTtsSpeechFile();
        String audioFilename = ttsFile.getAbsolutePath();

        mTTS.synthesizeToFile(text, null, new File(audioFilename), mUtteranceID);
        mTTS.setOnUtteranceProgressListener(new UtteranceProgressListener()
        {
            @Override
            public void onStart(String utteranceId)
            {
            }

            @Override
            public void onDone(String utteranceId)
            {
                if (utteranceId.equals(mUtteranceID)) {
                    aTalkApp.showToastMessage("Saved to " + audioFilename);
                }
            }

            @Override
            public void onError(String utteranceId)
            {
            }
        });
    }

    /**
     * Create the audio file if it does not exist
     *
     * @return Voice file for saving audio
     */
    private static File createTtsSpeechFile()
    {
        File ttsFile = null;
        File mediaDir = FileBackend.getaTalkStore(FileBackend.MEDIA_VOICE_SEND, true);
        if (!mediaDir.exists() && !mediaDir.mkdirs()) {
            Timber.d("Fail to create Media voice directory!");
            return null;
        }

        try {
            ttsFile = File.createTempFile("tts_", ".wav", mediaDir);
        } catch (IOException e) {
            Timber.d("Fail to create Media voice file!");
        }
        return ttsFile;
    }

    public static State getState()
    {
        return mState;
    }

    @Override
    protected void onDestroy()
    {
        if (mTTS != null) {
            mTTS.stop();
            mTTS.shutdown();
        }
        super.onDestroy();
    }
}