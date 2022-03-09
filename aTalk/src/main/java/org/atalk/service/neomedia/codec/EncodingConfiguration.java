/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.codec;

import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.service.neomedia.format.MediaFormatFactory;
import org.atalk.util.MediaType;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import timber.log.Timber;

/**
 * A base class manages encoding configurations. It holds information about supported formats.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public abstract class EncodingConfiguration
{
    /**
     * The <code>Comparator</code> which sorts the sets according to the settings in <code>encodingPreferences</code>.
     */
    private final Comparator<MediaFormat> encodingComparator = this::compareEncodingPreferences;

    /**
     * That's where we keep format preferences matching SDP formats to integers. We keep preferences
     * for both audio and video formats here in case we'd ever need to compare them to one another.
     * In most cases however both would be de-correlated and other components (such as the UI)
     * should present them separately.
     */
    protected final Map<String, Integer> encodingPreferences = new HashMap<>();

    /**
     * The cache of supported <code>AudioMediaFormat</code>s ordered by decreasing priority.
     */
    private Set<MediaFormat> supportedAudioEncodings;

    /**
     * The cache of supported <code>VideoMediaFormat</code>s ordered by decreasing priority.
     */
    private Set<MediaFormat> supportedVideoEncodings;

    /**
     * Updates the codecs in the supported sets according to the preferences in encodingPreferences.
     * If the preference value is <code>0</code>, the codec is disabled.
     */
    private void updateSupportedEncodings()
    {
        /*
         * If they need updating, their caches are invalid and need rebuilding next time they are requested.
         */
        supportedAudioEncodings = null;
        supportedVideoEncodings = null;
    }

    /**
     * Gets the <code>Set</code> of enabled available <code>MediaFormat</code>s with the specified
     * <code>MediaType</code> sorted in decreasing priority.
     *
     * @param type the <code>MediaType</code> of the <code>MediaFormat</code>s to get
     * @return a <code>Set</code> of enabled available <code>MediaFormat</code>s with the specified
     * <code>MediaType</code> sorted in decreasing priority
     */
    private Set<MediaFormat> updateSupportedEncodings(MediaType type)
    {
        Set<MediaFormat> enabled = new TreeSet<>(encodingComparator);

        for (MediaFormat format : getAllEncodings(type)) {
            if (getPriority(format) > 0)
                enabled.add(format);
        }
        return enabled;
    }

    /**
     * Sets <code>pref</code> as the preference associated with <code>encoding</code>. Use this method for
     * both audio and video encodings and don't worry if preferences are equal since we rarely need
     * to compare prefs of video encodings to those of audio encodings.
     *
     * @param encoding the SDP int of the encoding whose pref we're setting.
     * @param clockRate clock rate
     * @param pref a positive int indicating the preference for that encoding.
     */
    protected abstract void setEncodingPreference(String encoding, double clockRate, int pref);

    /**
     * Sets <code>priority</code> as the preference associated with <code>encoding</code>. Use this method
     * for both audio and video encodings and don't worry if the preferences are equal since we
     * rarely need to compare the preferences of video encodings to those of audio encodings.
     *
     * @param encoding the <code>MediaFormat</code> specifying the encoding to set the priority of
     * @param priority a positive <code>int</code> indicating the priority of <code>encoding</code> to set
     */
    public void setPriority(MediaFormat encoding, int priority)
    {
        String encodingEncoding = encoding.getEncoding();

        /*
         * Since we'll remember the priority in the ConfigurationService by associating it
         * with a property name/key based on encoding and clock rate only, it does not make sense to
         * store the MediaFormat in encodingPreferences because MediaFormat is much more specific
         * than just encoding and clock rate.
         */
        setEncodingPreference(encodingEncoding, encoding.getClockRate(), priority);
        updateSupportedEncodings();
    }

    /**
     * Get the priority for a <code>MediaFormat</code>.
     *
     * @param encoding the <code>MediaFormat</code>
     * @return the priority
     */
    public int getPriority(MediaFormat encoding)
    {
        /*
         * Directly returning encodingPreference.get(encoding) will throw a NullPointerException if
         * encodingPreferences does not contain a mapping for encoding.
         */
        Integer priority = encodingPreferences.get(getEncodingPreferenceKey(encoding));
        return (priority == null) ? 0 : priority;
    }

    /**
     * Returns all the available encodings for a specific <code>MediaType</code>. This includes disabled
     * ones (ones with priority 0).
     *
     * @param type the <code>MediaType</code> we would like to know the available encodings of
     * @return array of <code>MediaFormat</code> supported for the <code>MediaType</code>
     */
    public abstract MediaFormat[] getAllEncodings(MediaType type);

    /**
     * Returns the supported <code>MediaFormat</code>s i.e. the enabled available <code>MediaFormat</code>s,
     * sorted in decreasing priority. Returns only the formats of type <code>type</code>.
     *
     * @param type the <code>MediaType</code> of the supported <code>MediaFormat</code>s to get
     * @return an array of the supported <code>MediaFormat</code>s i.e. the enabled available
     * <code>MediaFormat</code>s sorted in decreasing priority. Returns only the formats of type
     * <code>type</code>.
     */
    public MediaFormat[] getEnabledEncodings(MediaType type)
    {
        Set<MediaFormat> supportedEncodings;

        switch (type) {
            case AUDIO:
                if (supportedAudioEncodings == null)
                    supportedAudioEncodings = updateSupportedEncodings(type);
                supportedEncodings = supportedAudioEncodings;
                break;
            case VIDEO:
                if (supportedVideoEncodings == null)
                    supportedVideoEncodings = updateSupportedEncodings(type);
                supportedEncodings = supportedVideoEncodings;
                break;
            default:
                return new MediaFormat[0];
        }
        return supportedEncodings.toArray(new MediaFormat[0]);
    }

    /**
     * Compares the two formats for order. Returns a negative integer, zero, or a positive integer
     * as the first format has been assigned a preference higher, equal to, or greater than the one
     * of the second.
     *
     * @param enc1 the first format to compare for preference.
     * @param enc2 the second format to compare for preference
     * @return a negative integer, zero, or a positive integer as the first format has been assigned
     * a preference higher, equal to, or greater than the one of the second
     */
    protected abstract int compareEncodingPreferences(MediaFormat enc1, MediaFormat enc2);

    /**
     * Gets the key in {@link #encodingPreferences} which is associated with the priority of a
     * specific <code>MediaFormat</code>.
     *
     * @param encoding the <code>MediaFormat</code> to get the key in {@link #encodingPreferences} of
     * @return the key in {@link #encodingPreferences} which is associated with the priority of the
     * specified <code>encoding</code>
     */
    protected String getEncodingPreferenceKey(MediaFormat encoding)
    {
        return encoding.getEncoding() + "/" + encoding.getClockRateString();
    }

    /**
     * Stores the format preferences in this instance in the given <code>Map</code>, using
     * <code>prefix</code> as a prefix to the key. Entries in the format (prefix+formatName,
     * formatPriority) will be added to <code>properties</code>, one for each available format. Note
     * that a "." is not automatically added to <code>prefix</code>.
     *
     * @param properties The <code>Map</code> where entries will be added.
     * @param prefix The prefix to use.
     */
    public void storeProperties(Map<String, String> properties, String prefix)
    {
        for (MediaType mediaType : MediaType.values()) {
            for (MediaFormat mediaFormat : getAllEncodings(mediaType)) {
                properties.put(prefix + getEncodingPreferenceKey(mediaFormat),
                        Integer.toString(getPriority(mediaFormat)));
            }
        }
    }

    /**
     * Stores the format preferences in this instance in the given <code>Map</code>. Entries in the
     * format (formatName, formatPriority) will be added to <code>properties</code>, one for each
     * available format.
     *
     * @param properties The <code>Map</code> where entries will be added.
     */
    public void storeProperties(Map<String, String> properties)
    {
        storeProperties(properties, "");
    }

    /**
     * Parses a <code>Map<String, String></code> and updates the format preferences according to it.
     * Does not use a prefix.
     *
     * @param properties The <code>Map</code> to parse.
     * @see EncodingConfiguration#loadProperties(java.util.Map, String)
     */
    public void loadProperties(Map<String, String> properties)
    {
        loadProperties(properties, "");
    }

    /**
     * Parses a <code>Map<String, String></code> and updates the format preferences according to it. For
     * each entry, if it's key does not begin with <code>prefix</code>, its ignored. If the key begins
     * with <code>prefix</code>, look for an encoding name after the last ".", and interpret the key
     * value as preference.
     *
     * @param properties The <code>Map</code> to parse.
     * @param prefix The prefix to use.
     */
    public void loadProperties(Map<String, String> properties, String prefix)
    {
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String pName = entry.getKey();
            String prefStr = entry.getValue();
            String fmtName;

            if (!pName.startsWith(prefix))
                continue;

            if (pName.contains("."))
                fmtName = pName.substring(pName.lastIndexOf('.') + 1);
            else
                fmtName = pName;

            // legacy
            if (fmtName.contains("sdp")) {
                fmtName = fmtName.replaceAll("sdp", "");
                /*
                 * If the current version of the property name is also associated with a value,
                 * ignore the value for the legacy one.
                 */
                if (properties.containsKey(pName.replaceAll("sdp", "")))
                    continue;
            }

            int preference;
            String encoding;
            double clockRate;

            try {
                preference = Integer.parseInt(prefStr);
                int encodingClockRateSeparator = fmtName.lastIndexOf('/');

                if (encodingClockRateSeparator > -1) {
                    encoding = fmtName.substring(0, encodingClockRateSeparator);
                    clockRate = Double.parseDouble(fmtName.substring(encodingClockRateSeparator + 1));
                }
                else {
                    encoding = fmtName;
                    clockRate = MediaFormatFactory.CLOCK_RATE_NOT_SPECIFIED;
                }
            } catch (NumberFormatException nfe) {
                Timber.w(nfe, "Failed to parse format (%s) of preference (%s).", fmtName, prefStr);
                continue;
            }
            setEncodingPreference(encoding, clockRate, preference);
        }

        // now update the arrays so that they are returned by order of preference.
        updateSupportedEncodings();
    }

    /**
     * Load the preferences stored in <code>encodingConfiguration</code>
     *
     * @param encodingConfiguration the <code>EncodingConfiguration</code> to load preferences from.
     */
    public void loadEncodingConfiguration(EncodingConfiguration encodingConfiguration)
    {
        Map<String, String> properties = new HashMap<>();
        encodingConfiguration.storeProperties(properties);
        loadProperties(properties);
    }

    /**
     * Returns <code>true</code> if there is at least one enabled format for media type <code>type</code>.
     *
     * @param mediaType The media type, MediaType.AUDIO or MediaType.VIDEO
     * @return <code>true</code> if there is at least one enabled format for media type <code>type</code>.
     */
    public boolean hasEnabledFormat(MediaType mediaType)
    {
        return (getEnabledEncodings(mediaType).length > 0);
    }
}
