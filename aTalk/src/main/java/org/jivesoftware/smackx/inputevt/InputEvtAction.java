/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jivesoftware.smackx.inputevt;

/**
 * Enumeration about the possible actions for an InputEvt IQ.
 *
 * @author Sebastien Vincent
 */
public enum InputEvtAction
{
    /**
     * The <code>notify</code> action.
     */
    NOTIFY("notify"),

    /**
     * The <code>start</code> action.
     */
    START("start"),

    /**
     * The <code>stop</code> action.
     */
    STOP("stop");

    /**
     * The name of this direction.
     */
    private final String actionName;

    /**
     * Creates a <code>InputEvtAction</code> instance with the specified name.
     *
     * @param actionName the name of the <code>InputEvtAction</code> we'd like to create.
     */
    private InputEvtAction(String actionName)
    {
        this.actionName = actionName;
    }

    /**
     * Returns the name of this <code>InputEvtAction</code>. The name returned by this method is meant
     * for use directly in the XMPP XML string.
     *
     * @return Returns the name of this <code>InputEvtAction</code>.
     */
    @Override
    public String toString()
    {
        return actionName;
    }

    /**
     * Returns a <code>InputEvtAction</code> value corresponding to the specified <code>inputActionStr</code>.
     *
     * @param inputActionStr the action <code>String</code> that we'd like to parse.
     * @return a <code>InputEvtAction</code> value corresponding to the specified <code>inputActionStr</code>.
     * @throws IllegalArgumentException in case <code>inputActionStr</code> is not valid
     */
    public static InputEvtAction fromString(String inputActionStr)
            throws IllegalArgumentException
    {
        for (InputEvtAction value : values())
            if (value.toString().equals(inputActionStr))
                return value;

        throw new IllegalArgumentException(inputActionStr + " is not a valid Input action");
    }
}
