/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.Logger;

import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.xdata.Form;
import org.jivesoftware.smackx.xdata.FormField;

import java.util.*;

/**
 * The Jabber implementation of the <tt>ChatRoomConfigurationForm</tt> interface.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class ChatRoomConfigurationFormJabberImpl implements ChatRoomConfigurationForm
{
    /**
     * The logger of this class.
     */
    private Logger logger = Logger.getLogger(ChatRoomConfigurationFormJabberImpl.class);

    /**
     * The smack chat room configuration form.
     */
    protected Form smackConfigForm;

    /**
     * The form that will be filled out and submitted by user.
     */
    protected Form smackSubmitForm;

    /**
     * The smack multi user chat is the one to which we'll send the form once filled out.
     */
    private MultiUserChat smackMultiUserChat;

    /**
     * Creates an instance of <tt>ChatRoomConfigurationFormJabberImpl</tt> by specifying the
     * corresponding smack multi user chat and smack configuration form.
     *
     * @param multiUserChat the smack multi user chat, to which we'll send the configuration form once filled out
     * @param smackConfigForm the smack configuration form
     */
    public ChatRoomConfigurationFormJabberImpl(MultiUserChat multiUserChat, Form smackConfigForm)
    {
        this.smackMultiUserChat = multiUserChat;
        this.smackConfigForm = smackConfigForm;
        this.smackSubmitForm = smackConfigForm.createAnswerForm();
    }

    /**
     * Returns an Iterator over a list of <tt>ChatRoomConfigurationFormFields</tt>.
     *
     * @return an Iterator over a list of <tt>ChatRoomConfigurationFormFields</tt>
     */
    public Iterator<ChatRoomConfigurationFormField> getConfigurationSet()
    {
        Vector<ChatRoomConfigurationFormField> configFormFields = new Vector<ChatRoomConfigurationFormField>();
        List<FormField> smackFormFields = smackConfigForm.getFields();

        for (FormField smackFormField : smackFormFields) {
            if (smackFormField == null || smackFormField.getType() == FormField.Type.hidden)
                continue;

            ChatRoomConfigurationFormFieldJabberImpl jabberConfigField = new ChatRoomConfigurationFormFieldJabberImpl(
                    smackFormField, smackSubmitForm);
            configFormFields.add(jabberConfigField);
        }

        return Collections.unmodifiableList(configFormFields).iterator();
    }

    /**
     * Sends the ready smack configuration form to the multi user chat.
     */
    public void submit()
            throws OperationFailedException
    {
        if (logger.isTraceEnabled())
            logger.trace("Sends chat room configuration form to the server.");

        try {
            smackMultiUserChat.sendConfigurationForm(smackSubmitForm);
        } catch (XMPPException | NoResponseException | InterruptedException
                | NotConnectedException e) {
            logger.error("Failed to submit the configuration form.", e);
            throw new OperationFailedException("Failed to submit the configuration form.",
                    OperationFailedException.GENERAL_ERROR);
        }
    }
}
