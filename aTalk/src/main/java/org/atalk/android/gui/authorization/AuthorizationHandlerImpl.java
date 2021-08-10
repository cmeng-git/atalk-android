/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.authorization;

import android.content.Context;
import android.content.Intent;

import net.java.sip.communicator.service.protocol.AuthorizationHandler;
import net.java.sip.communicator.service.protocol.AuthorizationRequest;
import net.java.sip.communicator.service.protocol.AuthorizationResponse;
import net.java.sip.communicator.service.protocol.Contact;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.dialogs.DialogActivity;

import java.util.HashMap;
import java.util.Map;

/**
 * Android implementation of <tt>AuthorizationHandler</tt>.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AuthorizationHandlerImpl implements AuthorizationHandler
{
    /**
     * The map of currently active <tt>AuthorizationRequestedHolder</tt>s.
     */
    private static Map<Long, AuthorizationRequestedHolder> requestMap = new HashMap<>();

    /**
     * Creates new instance of <tt>AuthorizationHandlerImpl</tt>.
     */
    public AuthorizationHandlerImpl()
    {
        // Clears the map after previous instance
        requestMap = new HashMap<>();
    }

    /**
     * Returns the <tt>AuthorizationRequestedHolder</tt> for given request <tt>id</tt>.
     *
     * @param id the request identifier.
     * @return the <tt>AuthorizationRequestedHolder</tt> for given request <tt>id</tt>.
     */
    public static AuthorizationRequestedHolder getRequest(Long id)
    {
        return requestMap.get(id);
    }

    /**
     * Implements the <tt>AuthorizationHandler.processAuthorisationRequest</tt> method.
     *
     * Called by the protocol provider whenever someone would like to add us to their contact list.
     */
    @Override
    public AuthorizationResponse processAuthorisationRequest(AuthorizationRequest req, Contact sourceContact)
    {
        Long id = System.currentTimeMillis();
        AuthorizationRequestedHolder requestHolder = new AuthorizationRequestedHolder(id, req, sourceContact);

        requestMap.put(id, requestHolder);
        AuthorizationRequestedDialog.showDialog(id);
        requestHolder.waitForResponse();
        requestMap.remove(id);

        return new AuthorizationResponse(requestHolder.responseCode, null);
    }

    /**
     * Implements the <tt>AuthorizationHandler.createAuthorizationRequest</tt> method.
     *
     * The method is called when the user has tried to add a contact to the contact list and this
     * contact requires authorization.
     */
    @Override
    public AuthorizationRequest createAuthorizationRequest(Contact contact)
    {
        AuthorizationRequest request = new AuthorizationRequest();
        Long id = System.currentTimeMillis();
        AuthorizationRequestedHolder requestHolder = new AuthorizationRequestedHolder(id, request, contact);

        requestMap.put(id, requestHolder);
        Intent dialogIntent = RequestAuthorizationDialog.getRequestAuthDialogIntent(id);
        aTalkApp.getGlobalContext().startActivity(dialogIntent);
        requestHolder.waitForResponse();

        // If user id did not cancel the dialog when return, prepared request and remove it
        // from the requestMap
        if (requestMap.containsKey(id)) {
            requestMap.remove(id);
            return requestHolder.request;
        }
        return null;
    }

    /**
     * Implements the <tt>AuthorizationHandler.processAuthorizationResponse</tt> method.
     *
     * The method will be called whenever someone acts upon an authorization request that we
     * have previously sent.
     */
    @Override
    public void processAuthorizationResponse(AuthorizationResponse response, Contact contact)
    {
        Context ctx = aTalkApp.getGlobalContext();
        String msg = contact.getAddress() + " ";

        AuthorizationResponse.AuthorizationResponseCode responseCode = response.getResponseCode();
        if (responseCode == AuthorizationResponse.ACCEPT) {
            msg += ctx.getString(R.string.service_gui_AUTHORIZATION_ACCEPTED);
        }
        else if (responseCode == AuthorizationResponse.REJECT) {
            msg += ctx.getString(R.string.service_gui_AUTHENTICATION_REJECTED);
        }

        String reason = response.getReason();
        if (StringUtils.isNotEmpty(reason)) {
            msg += " " + reason;
        }

        DialogActivity.showConfirmDialog(ctx,
                ctx.getString(R.string.service_gui_AUTHORIZATION_REQUEST), msg, null, null);
    }

    /**
     * Class used to store request state and communicate between this <tt>AuthorizationHandlerImpl
     * </tt> and dialog activities.
     */
    static public class AuthorizationRequestedHolder
    {
        /**
         * Request identifier.
         */
        public final Long ID;
        /**
         * The request object.
         */
        public final AuthorizationRequest request;
        /**
         * Contact related to the request.
         */
        public final Contact contact;
        /**
         * Lock object used to synchronize this handler with dialog activities.
         */
        private final Object responseLock = new Object();
        /**
         * Filed used to store response code set by the dialog activity.
         */
        public AuthorizationResponse.AuthorizationResponseCode responseCode;

        /**
         * Creates new instance of <tt>AuthorizationRequestedHolder</tt>.
         *
         * @param ID identifier assigned for the request
         * @param request the authorization request
         * @param contact contact related to the request
         */
        public AuthorizationRequestedHolder(Long ID, AuthorizationRequest request, Contact contact)
        {
            this.ID = ID;
            this.request = request;
            this.contact = contact;
        }

        /**
         * This method blocks until the dialog activity finishes its job.
         */
        public void waitForResponse()
        {
            synchronized (responseLock) {
                try {
                    responseLock.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        /**
         * Method should be used by the dialog activity to notify about the result.
         *
         * @param response
         */
        public void notifyResponseReceived(AuthorizationResponse.AuthorizationResponseCode response)
        {
            this.responseCode = response;
            releaseLock();
        }

        /**
         * Releases the synchronization lock.
         */
        private void releaseLock()
        {
            synchronized (responseLock) {
                responseLock.notifyAll();
            }
        }

        /**
         * Discards the request by removing it from active requests map and releasing the
         * synchronization lock.
         */
        public void discard()
        {
            requestMap.remove(ID);
            releaseLock();
        }

        /**
         * Submits request text and releases the synchronization lock.
         *
         * @param requestText the text that will be added to the authorization request.
         */
        public void submit(String requestText)
        {
            request.setReason(requestText);
            releaseLock();
        }
    }
}
