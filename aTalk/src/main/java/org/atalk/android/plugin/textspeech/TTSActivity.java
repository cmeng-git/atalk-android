package org.atalk.android.plugin.textspeech;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;

import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.aTalk;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.persistance.FileBackend;
import org.atalk.service.osgi.OSGiActivity;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import timber.log.Timber;

public class TTSActivity extends OSGiActivity implements TextToSpeech.OnInitListener, View.OnClickListener,
        CompoundButton.OnCheckedChangeListener {
    private static final String ACTION_TTS_SETTINGS = "com.android.settings.TTS_SETTINGS";

    private static final int ACT_CHECK_TTS_DATA = 1001;
    private static final int REQUEST_DEFAULT = 1003;

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
    private int requestCode = REQUEST_DEFAULT;

    public enum State {
        LOADING,
        DOWNLOAD_FAILED,
        ERROR,
        SUCCESS,
        UNKNOWN
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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

        // Use standard ActivityResultContract instead (both methods work)
        // ActivityResultLauncher<String> mGetTTSInfo = getTTSInfo();
        findViewById(R.id.tts_setting).setOnClickListener(view -> {
            requestCode = REQUEST_DEFAULT;
            // mGetTTSInfo.launch(ACTION_TTS_SETTINGS);
            mStartForResult.launch(new Intent(ACTION_TTS_SETTINGS));
        });

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
        aTalk.hasWriteStoragePermission(this, true);
        setState(State.LOADING);

        /*
         * Device without TTS engine will cause aTalk to crash; Check to see if we have TTS voice data
         * Launcher the voice data verifier.
         */
        try {
            requestCode = ACT_CHECK_TTS_DATA;
            // mGetTTSInfo.launch(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
            mStartForResult.launch(new Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA));

        } catch (ActivityNotFoundException ex) {
            aTalkApp.showToastMessage(ex.getMessage());
            finish();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        String tmp = ViewUtil.toString(mTtsDelay);
        if ((tmp != null) && !ttsDelay.equals(tmp)) {
            ConfigurationUtils.setTtsDelay(Integer.parseInt(tmp));
        }
    }

    private Locale getTtsLanguage() {
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
    public void onInit(int status) {
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
    public void onClick(View v) {
        String ttsText = ViewUtil.toString(mTtsText);
        switch (v.getId()) {
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
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView.getId() == R.id.tts_enable) {
            ConfigurationUtils.setTtsEnable(isChecked);
        }
    }

    /* Use standard ActivityResultContract instead */
//    /**
//     * GetTTSInfo class ActivityResultContract implementation.
//     */
//    public class GetTTSInfo extends ActivityResultContract<String, Integer>
//    {
//        @NonNull
//        @Override
//        public Intent createIntent(@NonNull Context context, @NonNull String action)
//        {
//            return new Intent(action);
//        }
//
//        @Override
//        public Integer parseResult(int resultCode, @Nullable Intent result)
//        {
//            if (((REQUEST_DEFAULT == requestCode) && resultCode != Activity.RESULT_OK || result == null)) {
//                return null;
//            }
//            return resultCode;
//        }
//    }
//
//    /**
//     * Handler for Activity Result callback
//     */
//    private ActivityResultLauncher<String> getTTSInfo()
//    {
//        return registerForActivityResult(new GetTTSInfo(), resultCode -> {
//            switch (requestCode) {
//                case ACT_CHECK_TTS_DATA:
//                    onDataChecked(resultCode);
//                    break;
//
//                case REQUEST_DEFAULT:
//                    initializeEngine();
//                    break;
//            }
//        });
//    }

    /**
     * standard ActivityResultContract#StartActivityForResult
     **/
    ActivityResultLauncher<Intent> mStartForResult = registerForActivityResult(new StartActivityForResult(), result -> {
        int resultCode = result.getResultCode();

        switch (requestCode) {
            case ACT_CHECK_TTS_DATA:
                onDataChecked(resultCode);
                break;

            case REQUEST_DEFAULT:
                if (resultCode == Activity.RESULT_OK) {
                    initializeEngine();
                }
                break;
        }
    });

    /**
     * Handles the result of voice data verification. If verification fails
     * following a successful installation, displays an error dialog. Otherwise,
     * either launches the installer or attempts to initialize the TTS engine.
     *
     * @param resultCode The result of voice data verification.
     */
    private void onDataChecked(int resultCode) {
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
    private void initializeEngine() {
        if (mTTS != null)
            mTTS = null;
        mTTS = new TextToSpeech(this, this);
    }

    /**
     * Sets the UI state.
     *
     * @param state The current state.
     */
    private void setState(State state) {
        mState = state;
        if (mState == State.LOADING) {
            findViewById(R.id.loading).setVisibility(View.VISIBLE);
            findViewById(R.id.success).setVisibility(View.GONE);
        }
        else {
            findViewById(R.id.loading).setVisibility(View.GONE);
            findViewById(R.id.success).setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NotNull String[] permissions, @NotNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == aTalk.PRC_WRITE_EXTERNAL_STORAGE) {
            if ((grantResults.length != 0) && (PackageManager.PERMISSION_GRANTED == grantResults[0]))
                permissionCount++;
        }
    }

    TextWatcher mTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            initButton();
        }

        @Override
        public void afterTextChanged(Editable s) {
            initButton();
        }
    };

    private void initButton() {
        boolean enable = (State.SUCCESS == mState) && (mTtsText.getText().length() > 0);
        btnPlay.setEnabled(enable);
        btnSave.setEnabled(enable);

        float alpha = enable ? 1.0f : 0.5f;
        btnPlay.setAlpha(alpha);
        btnSave.setAlpha(alpha);
    }

    private void saveToAudioFile(String text) {
        // Create tts audio file
        File ttsFile = createTtsSpeechFile();
        if (ttsFile == null) {
            return;
        }

        String audioFilename = ttsFile.getAbsolutePath();

        mTTS.synthesizeToFile(text, null, new File(audioFilename), mUtteranceID);
        mTTS.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
            }

            @Override
            public void onDone(String utteranceId) {
                if (utteranceId.equals(mUtteranceID)) {
                    aTalkApp.showToastMessage("Saved to " + audioFilename);
                }
            }

            @Override
            public void onError(String utteranceId) {
            }
        });
    }

    /**
     * Create the audio file if it does not exist
     *
     * @return Voice file for saving audio
     */
    private static File createTtsSpeechFile() {
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

    public static State getState() {
        return mState;
    }

    @Override
    protected void onDestroy() {
        if (mTTS != null) {
            mTTS.stop();
            mTTS.shutdown();
        }
        super.onDestroy();
    }
}