/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device;

import java.util.Objects;

import javax.media.CaptureDeviceInfo;
import javax.media.Format;
import javax.media.MediaLocator;

/**
 * Adds some important information (i.e. device type, UID.) to FMJ <code>CaptureDeviceInfo</code>.
 *
 * @author Vincent Lucas
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class CaptureDeviceInfo2 extends CaptureDeviceInfo
{
    /**
     * The device transport type.
     */
    private final String transportType;

    /**
     * The device UID (unique identifier).
     */
    private final String uid;

    /**
     * The persistent identifier for the model of this device.
     */
    private final String modelIdentifier;

    /**
     * Initializes a new <code>CaptureDeviceInfo2</code> instance from a specific
     * <code>CaptureDeviceInfo</code> instance and additional information specific to the
     * <code>CaptureDeviceInfo2</code> class. Because the properties of the specified
     * <code>captureDeviceInfo</code> are copied into the new instance, the constructor is to be used
     * when a <code>CaptureDeviceInfo</code> exists for other purposes already; otherwise, it is
     * preferable to use
     * {@link #CaptureDeviceInfo2(String, MediaLocator, Format[], String, String, String)} .
     *
     * @param captureDeviceInfo the <code>CaptureDeviceInfo</code> whose properties are to be copied into the new instance
     * @param uid the unique identifier of the hardware device (interface) which is to be represented by
     * the new instance
     * @param transportType the transport type (e.g. USB) of the device to be represented by the new instance
     * @param modelIdentifier the persistent identifier of the model of the hardware device to be represented
     * by the new instance
     */
    public CaptureDeviceInfo2(CaptureDeviceInfo captureDeviceInfo, String uid,
            String transportType, String modelIdentifier)
    {
        this(captureDeviceInfo.getName(), captureDeviceInfo.getLocator(), captureDeviceInfo
                .getFormats(), uid, transportType, modelIdentifier);
    }

    /**
     * Initializes a new <code>CaptureDeviceInfo2</code> instance with the specified name, media
     * locator, and array of Format objects.
     *
     * @param name the human-readable name of the new instance
     * @param locator the <code>MediaLocator</code> which uniquely identifies the device to be described by the
     * new instance
     * @param formats an array of the <code>Format</code>s supported by the device to be described by the new
     * instance
     * @param uid the unique identifier of the hardware device (interface) which is to be represented by
     * the new instance
     * @param transportType the transport type (e.g. USB) of the device to be represented by the new instance
     * @param modelIdentifier the persistent identifier of the model of the hardware device to be represented
     * by the new instance
     */
    public CaptureDeviceInfo2(String name, MediaLocator locator, Format[] formats, String uid,
            String transportType, String modelIdentifier)
    {
        super(name, locator, formats);

        this.uid = uid;
        this.transportType = transportType;
        this.modelIdentifier = modelIdentifier;
    }

    /**
     * Determines whether a specific <code>Object</code> is equal (by value) to this instance.
     *
     * @param obj the <code>Object</code> to be determined whether it is equal (by value) to this instance
     * @return <code>true</code> if the specified <code>obj</code> is equal (by value) to this instance; otherwise, <code>false</code>
     */
    @Override
    public boolean equals(Object obj)
    {
        if (obj == null)
            return false;
        else if (obj == this)
            return true;
        else if (obj instanceof CaptureDeviceInfo2) {
            CaptureDeviceInfo2 cdi2 = (CaptureDeviceInfo2) obj;

            // locator
            MediaLocator locator = getLocator();
            MediaLocator cdi2Locator = cdi2.getLocator();

            if (locator == null) {
                if (cdi2Locator != null)
                    return false;
            }
            else if (cdi2Locator == null)
                return false;
            else {
                // protocol
                String protocol = locator.getProtocol();
                String cdi2Protocol = cdi2Locator.getProtocol();

                if (protocol == null) {
                    if (cdi2Protocol != null)
                        return false;
                }
                else if (cdi2Protocol == null)
                    return false;
                else if (!protocol.equals(cdi2Protocol))
                    return false;
            }

            // identifier
            return getIdentifier().equals(cdi2.getIdentifier());
        }
        else
            return false;
    }

    /**
     * Returns the device identifier used to save and load device preferences. It is composed by the
     * system UID if not null. Otherwise returns the device name and (if not null) the transport type.
     *
     * @return The device identifier.
     */
    public String getIdentifier()
    {
        return (uid == null) ? name : uid;
    }

    /**
     * Returns the device transport type of this instance.
     *
     * @return the device transport type of this instance
     */
    public String getTransportType()
    {
        return transportType;
    }

    /**
     * Returns the device UID (unique identifier) of this instance.
     *
     * @return the device UID (unique identifier) of this instance
     */
    public String getUID()
    {
        return uid;
    }

    /**
     * Returns the model identifier of this instance.
     *
     * @return the model identifier of this instance
     */
    public String getModelIdentifier()
    {
        return (modelIdentifier == null) ? name : modelIdentifier;
    }

    /**
     * Returns a hash code value for this object for the benefit of hashtables.
     *
     * @return a hash code value for this object for the benefit of hashtables
     */
    @Override
    public int hashCode()
    {
        return getIdentifier().hashCode();
    }

    /**
     * Determines whether a specific transport type is equal to/the same as the transport type of
     * this instance.
     *
     * @param transportType the transport type to compare to the transport type of this instance
     * @return <code>true</code> if the specified <code>transportType</code> is equal to/the same as the
     * transport type of this instance; otherwise, <code>false</code>
     */
    public boolean isSameTransportType(String transportType)
    {
        return Objects.equals(this.transportType, transportType);
    }
}
