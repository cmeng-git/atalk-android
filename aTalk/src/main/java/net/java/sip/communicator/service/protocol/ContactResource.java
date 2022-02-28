/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

/**
 * The <code>ContactResource</code> class represents a resource, from which a <code>Contact</code> is connected.
 *
 * @author Yana Stamcheva
 */
public class ContactResource
{
    /**
     * A static instance of this class representing the base resource. If this base resource is
     * passed as a parameter for any operation (send message, call) the operation should explicitly
     * use the base contact address. This is meant to force a call or a message sending to all the
     * resources for the corresponding contact.
     */
    public static ContactResource BASE_RESOURCE = new ContactResource();

    /**
     * The contact, to which this resource belongs.
     */
    private Contact contact;

    /**
     * The name of this contact resource.
     */
    private String resourceName;

    /**
     * The presence status of this contact resource.
     */
    protected PresenceStatus presenceStatus;

    /**
     * The priority of this contact source.
     */
    protected int priority;

    /**
     * Whether this contact resource is a mobile one.
     */
    protected boolean mobile = false;

    /**
     * Creates an empty instance of <code>ContactResource</code> representing the base resource.
     */
    public ContactResource()
    {
    }

    /**
     * Creates a <code>ContactResource</code> by specifying the <code>resourceName</code>, the
     * <code>presenceStatus</code> and the <code>priority</code>.
     *
     * @param contact the parent <code>Contact</code> this resource is about
     * @param resourceName the name of this resource
     * @param presenceStatus the presence status of this resource
     * @param priority the priority of this resource
     */
    public ContactResource(Contact contact, String resourceName, PresenceStatus presenceStatus,
            int priority, boolean mobile)
    {
        this.contact = contact;
        this.resourceName = resourceName;
        this.presenceStatus = presenceStatus;
        this.priority = priority;
        this.mobile = mobile;
    }

    /**
     * Returns the <code>Contact</code>, this resources belongs to.
     *
     * @return the <code>Contact</code>, this resources belongs to
     */
    public Contact getContact()
    {
        return contact;
    }

    /**
     * Returns the name of this resource.
     *
     * @return the name of this resource
     */
    public String getResourceName()
    {
        return resourceName;
    }

    /**
     * Returns the presence status of this resource.
     *
     * @return the presence status of this resource
     */
    public PresenceStatus getPresenceStatus()
    {
        return presenceStatus;
    }

    /**
     * Returns the priority of the resources.
     *
     * @return the priority of this resource
     */
    public int getPriority()
    {
        return priority;
    }

    /**
     * Whether contact is mobile one. Logged in only from mobile device.
     *
     * @return whether contact is mobile one.
     */
    public boolean isMobile()
    {
        return mobile;
    }
}
