package net.java.sip.communicator.impl.protocol.jabber;

import org.jivesoftware.smack.XMPPException;

/**
 * An implementation XMPPException for aTalk: mainly use of securityException
 *
 * @author Eng Chong Meng
 */
public class ATalkXmppException extends XMPPException
{
    /**
     * @param ex the original exception root cause
     */
    public ATalkXmppException(String message)
    {
        super(message);
    }

    /**
     * @param message the exception message
     * @param ex the original exception root cause
     */
    public ATalkXmppException(String message, Exception ex)
    {
        super(message, ex);
    }
}
