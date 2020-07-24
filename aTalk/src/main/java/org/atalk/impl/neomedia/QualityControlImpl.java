/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia;

import org.atalk.android.util.java.awt.Dimension;
import org.atalk.impl.neomedia.device.DeviceConfiguration;
import org.atalk.service.neomedia.QualityControl;
import org.atalk.service.neomedia.QualityPreset;

import timber.log.Timber;

/**
 * Implements {@link QualityControl} for the purposes of {@link VideoMediaStreamImpl}.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class QualityControlImpl implements QualityControl
{
    /**
     * This is the local settings from the configuration panel.
     */
    private QualityPreset localSettingsPreset;

    /**
     * The maximum values for resolution, and framerate etc.
     */
    private QualityPreset maxPreset;

    /**
     * The current used preset.
     */
    private QualityPreset preset;

    /**
     * Sets the preset.
     *
     * @param preset the desired video settings.
     */
    private void setRemoteReceivePreset(QualityPreset preset)
    {
        QualityPreset preferredSendPreset = getPreferredSendPreset();

        if (preset.compareTo(preferredSendPreset) > 0)
            this.preset = preferredSendPreset;
        else {
            this.preset = preset;

            Dimension resolution;
            if ((resolution = preset.getResolution()) != null) {
                Timber.i("video send resolution: %dx%d", resolution.width, resolution.height);
            }
        }
    }

    /**
     * The current preset.
     *
     * @return the current preset
     */
    public QualityPreset getRemoteReceivePreset()
    {
        return preset;
    }

    /**
     * The minimum resolution values for remote part.
     *
     * @return minimum resolution values for remote part.
     */
    public QualityPreset getRemoteSendMinPreset()
    {
        /* We do not support such a value at the time of this writing. */
        return null;
    }

    /**
     * The max resolution values for remote part.
     *
     * @return max resolution values for remote part.
     */
    public QualityPreset getRemoteSendMaxPreset()
    {
        return maxPreset;
    }

    /**
     * Does nothing specific locally.
     *
     * @param preset the max preset
     */
    public void setPreferredRemoteSendMaxPreset(QualityPreset preset)
    {
        setRemoteSendMaxPreset(preset);
    }

    /**
     * Changes remote send preset, the one we will receive.
     *
     * @param preset
     */
    public void setRemoteSendMaxPreset(QualityPreset preset)
    {
        this.maxPreset = preset;
    }

    /**
     * Gets the local setting of capture.
     *
     * @return the local setting of capture
     */
    private QualityPreset getPreferredSendPreset()
    {
        if (localSettingsPreset == null) {
            DeviceConfiguration devCfg = NeomediaServiceUtils.getMediaServiceImpl().getDeviceConfiguration();
            localSettingsPreset = new QualityPreset(devCfg.getVideoSize(), devCfg.getFrameRate());
        }
        return localSettingsPreset;
    }

    /**
     * Sets maximum resolution.
     *
     * @param res
     */
    public void setRemoteReceiveResolution(Dimension res)
    {
        setRemoteReceivePreset(new QualityPreset(res));
    }
}
