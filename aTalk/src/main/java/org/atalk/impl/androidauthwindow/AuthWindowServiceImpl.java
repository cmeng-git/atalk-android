/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidauthwindow;

import net.java.sip.communicator.service.gui.AuthenticationWindowService;

import java.util.HashMap;
import java.util.Map;

/**
 * Android implementation of <code>AuthenticationWindowService</code>. This class manages authentication requests. Each
 * request data is held by the <code>AuthWindowImpl</code> identified by assigned request id. Request id is passed to the
 * <code>AuthWindowActivity</code> so that it can obtain request data and interact with the user.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AuthWindowServiceImpl implements AuthenticationWindowService
{
    /**
     * Requests map
     */
    private static Map<Long, AuthWindowImpl> requestMap = new HashMap<>();

    /**
     * Creates an instance of the <code>AuthenticationWindow</code> implementation.
     *
     * @param server the server name
     * @param isUserNameEditable indicates if the user name is editable
     * @param icon the icon to display on the left of the authentication window
     * @param windowTitle customized window title
     * @param windowText customized window text
     * @param usernameLabel customized username field label text
     * @param passwordLabel customized password field label text
     * @param errorMessage an error message if this dialog is shown to indicate the user that something went wrong
     * @param signupLink an URL that allows the user to sign up
     */
    public AuthenticationWindow create(String userName, char[] password, String server, boolean isUserNameEditable,
            boolean isRememberPassword, Object icon, String windowTitle, String windowText, String usernameLabel,
            String passwordLabel, String errorMessage, String signupLink)
    {
        long requestId = System.currentTimeMillis();
        AuthWindowImpl authWindow = new AuthWindowImpl(requestId, userName, password, server, isUserNameEditable,
                isRememberPassword, windowTitle, windowText, usernameLabel, passwordLabel);

        requestMap.put(requestId, authWindow);
        return authWindow;
    }

    /**
     * Returns <code>AuthWindowImpl</code> for given <code>requestId</code>.
     *
     * @param requestId the request identifier
     * @return <code>AuthWindowImpl</code> identified by given <code>requestId</code>.
     */
    static AuthWindowImpl getAuthWindow(long requestId)
    {
        return requestMap.get(requestId);
    }

    /**
     * Called when authentication request processing for given <code>requestId</code> is completed or canceled.
     *
     * @param requestId the request identifier
     */
    static void clearRequest(long requestId)
    {
        requestMap.remove(requestId);
    }
}
