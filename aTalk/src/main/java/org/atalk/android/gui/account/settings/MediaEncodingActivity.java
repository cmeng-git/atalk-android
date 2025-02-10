/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.java.sip.communicator.service.protocol.EncodingsRegistrationUtil;
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory;

import org.atalk.android.BaseActivity;
import org.atalk.android.R;
import org.atalk.android.gui.actionbar.ActionBarToggleFragment;
import org.atalk.impl.neomedia.MediaServiceImpl;
import org.atalk.impl.neomedia.MediaUtils;
import org.atalk.impl.neomedia.NeomediaActivator;
import org.atalk.service.neomedia.codec.EncodingConfiguration;
import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.util.MediaType;
import org.jetbrains.annotations.NotNull;

/**
 * Activity allows user to edit audio or video encodings settings and set their priority.
 * The intent starting this activity must be parametrized with:<br/>
 * - {@link #ENC_MEDIA_TYPE_KEY} with {@link MediaType} which specifies if audio or video encoding will be edited<br/>
 * - {@link #EXTRA_KEY_ENC_REG} with {@link EncodingsRegistrationUtil} instance which  encoding properties<br/>
 * <br/>
 * After activity finishes it's job it return in {@link Intent} the
 * {@link EncodingsRegistrationUtil} under its key and additional <code>boolean</code> flag
 * indicating whether any changes has been made under key: {@link #EXTRA_KEY_HAS_CHANGES}.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class MediaEncodingActivity extends BaseActivity
        implements ActionBarToggleFragment.ActionBarToggleModel {
    /**
     * The intent's key for {@link MediaType}
     */
    public static final String ENC_MEDIA_TYPE_KEY = "media_type";

    /**
     * The intent's key for {@link EncodingsRegistrationUtil}
     */
    public static final String EXTRA_KEY_ENC_REG = "encRegObj";

    /**
     * The intent's key for flag indicating whether any changes has been made
     */
    public static final String EXTRA_KEY_HAS_CHANGES = "encHasChanges";

    /**
     * State key for encodings registration object.
     */
    private static final String STATE_ENC_REG = "state_enc_reg";

    /**
     * State key for "has changes" flag.
     */
    private static final String STATE_HAS_CHANGES = "state_has_changes";

    /**
     * Holds the properties we need to get/set for the encoding preferences
     */
    private final Map<String, String> encodingProperties = new HashMap<>();

    /**
     * The encoding configuration object used to manipulate on the properties
     */
    private EncodingConfiguration encodingConfiguration;

    /**
     * Flag storing info if the global settings are overridden
     */
    private boolean isOverrideEncodings;

    /**
     * The {@link EncodingsRegistrationUtil} object that stores encoding and their priorities
     */
    private EncodingsRegistrationUtil mEncReg;

    /**
     * Flag indicating whether any changes has been made to the configuration
     */
    private boolean hasChanges = false;

    /**
     * The encodings edit list fragment
     */
    private MediaEncodingsFragment encodingsFragment;

    /**
     * Audio or video media type that is currently used.
     */
    private MediaType mediaType;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadEncodings(savedInstanceState);

        if (savedInstanceState == null) {
            ActionBarToggleFragment toggleFragment
                    = ActionBarToggleFragment.newInstance(getString(R.string.enc_override_global));
            getSupportFragmentManager().beginTransaction()
                    .add(toggleFragment, "action_bar_toggle")
                    .commit();
        }
    }

    /**
     * Loads properties passed by intent's extras and initializes the activity.
     *
     * @param savedInstanceState bundle that contains the state or <code>null</code> if the <code>Activity</code> was just
     * created.
     */
    private void loadEncodings(Bundle savedInstanceState) {
        Intent intent = getIntent();
        if (savedInstanceState == null) {
            mEncReg = (EncodingsRegistrationUtil) intent.getSerializableExtra(EXTRA_KEY_ENC_REG);
        }
        else {
            mEncReg = (EncodingsRegistrationUtil) savedInstanceState.getSerializable(STATE_ENC_REG);
            hasChanges = savedInstanceState.getBoolean(STATE_HAS_CHANGES);
        }

        isOverrideEncodings = mEncReg.isOverrideEncodings();
        Map<String, String> encodingProperties = mEncReg.getEncodingProperties();

        MediaServiceImpl mediaServiceImpl = NeomediaActivator.getMediaServiceImpl();
        if (mediaServiceImpl != null) {
            this.encodingConfiguration = mediaServiceImpl.createEmptyEncodingConfiguration();
            encodingConfiguration.loadProperties(encodingProperties, ProtocolProviderFactory.ENCODING_PROP_PREFIX);
            encodingConfiguration.storeProperties(encodingProperties,
                    ProtocolProviderFactory.ENCODING_PROP_PREFIX + ".");

            this.mediaType = (MediaType) intent.getSerializableExtra(ENC_MEDIA_TYPE_KEY);
            if (savedInstanceState == null) {
                List<MediaFormat> encodings = getEncodings(encodingConfiguration, mediaType);
                List<Integer> priorities = getPriorities(encodings, encodingConfiguration);
                List<String> encodingsStrs = getEncodingsStr(encodings.iterator());
                this.encodingsFragment = MediaEncodingsFragment.newInstance(encodingsStrs, priorities);
                getSupportFragmentManager().beginTransaction().replace(android.R.id.content, encodingsFragment).commit();
            }
            else {
                this.encodingsFragment = (MediaEncodingsFragment) getSupportFragmentManager().findFragmentById(android.R.id.content);
            }
        }
    }

    /**
     * Returns the string representing encoding on the list
     *
     * @param mf {@link MediaFormat} for encoding list row
     *
     * @return the string representing encoding list item
     */
    private static String getEncodingStr(MediaFormat mf) {
        return mf.getEncoding() + "/" + mf.getClockRateString();
    }

    /**
     * Initializes encodings list
     */
    public static List<MediaFormat> getEncodings(final EncodingConfiguration encodingConfig, MediaType mediaType) {
        MediaFormat[] availableEncodings = encodingConfig.getAllEncodings(mediaType);
        HashMap<String, MediaFormat> availableEncodingSet = new HashMap<>();

        for (MediaFormat availableEncoding : availableEncodings) {
            availableEncodingSet.put(availableEncoding.getEncoding() + "/"
                    + availableEncoding.getClockRateString(), availableEncoding);
        }
        availableEncodings = availableEncodingSet.values().toArray(MediaUtils.EMPTY_MEDIA_FORMATS);
        int encodingCount = availableEncodings.length;

        MediaFormat[] encodings = new MediaFormat[encodingCount];
        System.arraycopy(availableEncodings, 0, encodings, 0, encodingCount);

        // Display the encodings in decreasing priority.
        Arrays.sort(encodings, 0, encodingCount, (format0, format1) -> {
            int ret = encodingConfig.getPriority(format1) - encodingConfig.getPriority(format0);

            if (ret == 0) {
                ret = format0.getEncoding().compareToIgnoreCase(format1.getEncoding());
                if (ret == 0) {
                    ret = Double.compare(format1.getClockRate(), format0.getClockRate());
                }
            }
            return ret;
        });

        List<MediaFormat> outList = new ArrayList<>(encodings.length);
        Collections.addAll(outList, encodings);

        return outList;
    }

    /**
     * Creates string representation for given set of <code>MediaFormat</code>s.
     *
     * @param encodings the iterator with <code>MediaFormat</code> to be converted into strings.
     *
     * @return string representation of given <code>MediaFormat</code>s.
     */
    public static List<String> getEncodingsStr(Iterator<MediaFormat> encodings) {
        List<String> outList = new ArrayList<>();
        while (encodings.hasNext()) {
            outList.add(getEncodingStr(encodings.next()));
        }
        return outList;
    }

    /**
     * Select <code>MediaFormat</code> by string representation created using {@link #getEncodingStr(MediaFormat)}.
     *
     * @param encodings set of <code>MediaFormat</code>s from which the one will be selected.
     * @param str string representation of <code>MediaFormat</code>.
     *
     * @return selected <code>MediaFormat</code> from the given set by matching string representation.
     */
    public static MediaFormat getEncodingFromStr(Iterator<MediaFormat> encodings, String str) {
        while (encodings.hasNext()) {
            MediaFormat mf = encodings.next();
            if (getEncodingStr(mf).equals(str)) {
                return mf;
            }
        }
        throw new IllegalArgumentException("Invalid format str: " + str);
    }

    /**
     * Gets the priorities list for given set of encodings and encodings configuration.
     *
     * @param encodings the set of encodings that will be used.
     * @param encodingConfig encodings configuration that stores priorities.
     *
     * @return priorities list for given set of encodings.
     */
    public static List<Integer> getPriorities(List<MediaFormat> encodings,
            EncodingConfiguration encodingConfig) {
        int count = encodings.size();
        List<Integer> priorities = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            int current = encodingConfig.getPriority(encodings.get(i));
            int orderPriority = MediaEncodingsFragment.calcPriority(encodings, i);
            priorities.add(current > 0 ? orderPriority : 0);
        }
        return priorities;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onSaveInstanceState(@NotNull Bundle outState) {
        super.onSaveInstanceState(outState);
        commitChanges();

        outState.putSerializable(STATE_ENC_REG, mEncReg);
        outState.putBoolean(STATE_HAS_CHANGES, hasChanges);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        encodingsFragment.setEnabled(isOverrideEncodings);

        return true;
    }

    /**
     * Catches the back key and returns edited state in <code>Intent</code> extra. <br/>
     * {@inheritDoc}
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // Catch the back key code and store results
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            commitChanges();

            Intent result = new Intent();
            result.putExtra(EXTRA_KEY_ENC_REG, mEncReg);

            if (encodingsFragment != null)
                hasChanges = hasChanges || encodingsFragment.hasChanges();
            result.putExtra(EXTRA_KEY_HAS_CHANGES, hasChanges);

            setResult(Activity.RESULT_OK, result);
            finish();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * Commits priorities edited by the <code>EncodingsFragment</code> into given
     * <code>EncodingConfiguration</code>.
     *
     * @param encodingConf configuration that will store encodings priorities.
     * @param mediaType audio or video media type that was edited.
     * @param encFragment the fragment which edited encodings priorities.
     */
    public static void commitPriorities(EncodingConfiguration encodingConf, MediaType mediaType,
            MediaEncodingsFragment encFragment) {
        if (!encFragment.hasChanges()) {
            return;
        }

        List<MediaFormat> formats = getEncodings(encodingConf, mediaType);
        List<String> encodings = encFragment.getEncodings();
        List<Integer> priorities = encFragment.getPriorities();
        for (int i = 0; i < encodings.size(); i++) {
            encodingConf.setPriority(getEncodingFromStr(formats.iterator(), encodings.get(i)), priorities.get(i));
        }
    }

    /**
     * Commits user changes.
     */
    private void commitChanges() {
        commitPriorities(encodingConfiguration, mediaType, encodingsFragment);

        encodingConfiguration.storeProperties(encodingProperties,
                ProtocolProviderFactory.ENCODING_PROP_PREFIX + ".");

        mEncReg.setOverrideEncodings(isOverrideEncodings);
        mEncReg.setEncodingProperties(encodingProperties);
    }

    @Override
    public boolean isChecked() {
        return isOverrideEncodings;
    }

    @Override
    public void setChecked(boolean isChecked) {
        isOverrideEncodings = isChecked;
        encodingsFragment.setEnabled(isOverrideEncodings);
        hasChanges = true;
    }
}
