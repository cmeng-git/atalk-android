/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014~2022 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.service.httputil;

import android.text.TextUtils;

import net.java.sip.communicator.service.gui.AuthenticationWindowService;

import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.HttpClientContext;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.List;

import javax.net.ssl.SSLContext;

import okhttp3.Call;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import timber.log.Timber;

/**
 * Common http utils querying http locations, handling redirects, self-signed certificates, host verify
 * on certificates, password protection and storing and reusing credentials for password protected sites.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class HttpUtils
{
    /**
     * The prefix used when storing credentials for sites when no property is provided.
     */
    private static final String HTTP_CREDENTIALS_PREFIX = "http.credential.";

    private static final String USER_NAME = "username";
    private static final String PASSWORD = "password";

    /*
     * Local global variables use in getHttpClient() and in other methods if required;
     */
    private static HTTPCredentialsProvider credentialsProvider;

    /**
     * Executes the method and return the result. Handle ask for password when hitting password protected site.
     * Keep asking for password till user clicks cancel or enters correct password.
     * When 'remember password' is checked password is saved, if this
     * password and username are not correct clear them, if there are correct they stay saved.
     *
     * @param httpClient the configured http client to use.
     * @param postRequest the request for now it is get or post.
     * @param redirectHandler handles redirection, should we redirect and the actual redirect.
     * @param parameters if we are redirecting we can use already filled
     * username and password in order to avoid asking the user twice.
     * @return the result http entity.
     */
    private static ResponseBody executeMethod(OkHttpClient httpClient, Request postRequest)
            throws Throwable
    {
        // do it when response (first execution) or till we are unauthorized
        Response response = null;
        Call cancelableCall;

        while (response == null
                || (HttpStatus.SC_UNAUTHORIZED == response.code())
                || (HttpStatus.SC_FORBIDDEN == response.code())) {
            // if we were unauthorized, lets clear the method and recreate it for new connection with new credentials.
            if ((response != null)
                    && ((HttpStatus.SC_UNAUTHORIZED == response.code())
                    || (HttpStatus.SC_FORBIDDEN == response.code()))) {
                Timber.d("Will retry http connect and credentials input as latest are not correct!");
                throw new AuthenticationException("Authorization needed");
            }
            else {
                Timber.i("Auto checking for software update: %s", postRequest);
                cancelableCall = httpClient.newCall(postRequest);
                response = cancelableCall.execute();
            }

            // if user click cancel no need to retry, stop trying
            if (!credentialsProvider.retry()) {
                Timber.d("User canceled credentials input.");
                cancelableCall.cancel();
                break;
            }
        }

        // if we finally managed to login return the result or null if user has cancelled.
        return (HttpStatus.SC_OK == response.code()) ? response.body() : null;
    }

    /**
     * Posting form to <tt>url</tt>. For submission we use POST method i.e. "application/x-www-form-urlencoded" encoded.
     *
     * @param url HTTP address.
     * @param usernamePropertyName the property to use to retrieve/store username value
     * if protected site is hit, for username ConfigurationService service is used.
     * @param passwordPropertyName the property to use to retrieve/store password value
     * if protected site is hit, for password CredentialsStorageService service is used.
     * @param jsonBody the Json parameter to include in post.
     * @param username the username parameter if any, otherwise null
     * @param password the password parameter if any, otherwise null
     * @param headerParamNames additional header name to include
     * @param headerParamValues corresponding header value to include
     * @return the result or throws IOException
     */
    public static HTTPResponseResult postForm(String url, String usernamePropertyName, String passwordPropertyName,
            JSONObject jsonObject, String username, String password,
            List<String> headerParamNames, List<String> headerParamValues)
            throws IOException
    {
        RequestBody requestJsonBody = RequestBody.create(
                jsonObject.toString(),
                MediaType.parse("application/json")
        );

        Request.Builder prBuilder = new Request.Builder()
                .url(url)
                .post(requestJsonBody);

        if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(password))
            prBuilder.addHeader("Authorization", Credentials.basic(username, password));

        if (headerParamNames != null && headerParamValues != null) {
            for (int i = 0; i < headerParamNames.size(); i++) {
                prBuilder.addHeader(headerParamNames.get(i), headerParamValues.get(i));
            }
        }

        OkHttpClient httpClient = HttpConnectionManager.buildHttpClient(url, 10);
        Response response = httpClient.newCall(prBuilder.build()).execute();
        return new HTTPResponseResult(response.body(), httpClient);
    }

    /**
     * Returns the preconfigured http client, using CertificateVerificationService, timeouts, user-agent,
     * hostname verifier, proxy settings are used from global java settings,
     * if protected site is hit asks for credentials using util.swing.AuthenticationWindow.
     *
     * @param usernamePropertyName the property uses to retrieve/store username value
     * if protected site is hit; for username retrieval, the ConfigurationService service is used.
     * @param passwordPropertyName the property uses to retrieve/store password value
     * if protected site is hit, for password retrieval, the  CredentialsStorageService service is used.
     * @param credentialsProvider if not null provider will bre reused in the new client
     * @param url the HTTP url we will be connecting to
     */
//    public static DefaultHttpClient getHttpClient(final String url,
//            CredentialsProvider credentialsProvider, RedirectStrategy redirectStrategy)
//            throws IOException
    public static OkHttpClient getHttpClient(final String url)
            throws IOException
    {

        SSLContext sslContext = null;
        try {
            sslContext = HttpUtilActivator.getCertificateVerificationService()
                    .getSSLContext(HttpUtilActivator.getCertificateVerificationService().getTrustManager(url));
        } catch (GeneralSecurityException e) {
            throw new IOException(e.getMessage());
        }

        return HttpConnectionManager.buildHttpClient(url, 10);
    }

    /**
     * The provider asking for the password that is inserted into httpclient.
     */
    private static class HTTPCredentialsProvider implements CredentialsProvider
    {
        /**
         * Should we continue retrying, this is set when user hits cancel.
         */
        private boolean retry = true;

        /**
         * The last scope we have used, no problem overriding cause
         * we use new HTTPCredentialsProvider instance for every httpclient/request.
         */
        private AuthScope usedScope = null;

        /**
         * The property uses to retrieve/store username value if protected site is hit,
         * for the username retrieval, ConfigurationService service is used.
         */
        private String usernamePropertyName;

        /**
         * The property uses to retrieve/store password value if protected site is hit,
         * for the password retrieval, CredentialsStorageService service is used.
         */
        private String passwordPropertyName;

        /**
         * Authentication username if any.
         */
        private String authUsername = null;

        /**
         * Authentication password if any.
         */
        private String authPassword = null;

        /**
         * Error message.
         */
        private String errorMessage = null;

        /**
         * Creates HTTPCredentialsProvider.
         *
         * @param usernamePropertyName the property uses to retrieve/store username value
         * if protected site is hit; for username retrieval, the ConfigurationService service is used.
         * @param passwordPropertyName the property uses to retrieve/store password value
         * if protected site is hit, for password retrieval, the  CredentialsStorageService service is used.
         */
        HTTPCredentialsProvider(String usernamePropertyName, String passwordPropertyName)
        {
            this.usernamePropertyName = usernamePropertyName;
            this.passwordPropertyName = passwordPropertyName;
        }

        /**
         * Not used.
         */
        @Override
        public void setCredentials(AuthScope authscope, org.apache.http.auth.Credentials credentials)
        {
        }

        /**
         * Get the {@link org.apache.hc.client5.http.auth.Credentials Credentials} for the given authentication scope.
         *
         * @param authScope the {@link org.apache.hc.client5.http.auth.AuthScope authentication scope}
         * @return the credentials
         * @see #setCredentials(org.apache.hc.client5.http.auth.AuthScope, org.apache.hc.client5.http.auth.Credentials)
         */
        // @Override
        public org.apache.http.auth.Credentials getCredentials(AuthScope authScope)
        {
            this.usedScope = authScope;

            // Use specified password and username property if provided, else create one from the scope/site we are connecting to.
            // cmeng: same property name for both??? so changed
            if (passwordPropertyName == null)
                passwordPropertyName = getCredentialProperty(authScope, PASSWORD);
            if (usernamePropertyName == null)
                usernamePropertyName = getCredentialProperty(authScope, USER_NAME);

            // load the password; if password is not saved ask user for credentials
            authPassword = HttpUtilActivator.getCredentialsService().loadPassword(passwordPropertyName);
            if (authPassword == null) {
                AuthenticationWindowService authenticationWindowService = HttpUtilActivator.getAuthenticationWindowService();
                if (authenticationWindowService == null) {
                    Timber.e("No AuthenticationWindowService implementation");
                    return null;
                }

                AuthenticationWindowService.AuthenticationWindow authWindow = authenticationWindowService.create(
                        authUsername, null, authScope.getHost(), true, false,
                        null, null, null, null, null, errorMessage,
                        HttpUtilActivator.getResources().getSettingsString("plugin.provisioning.SIGN_UP_LINK"));
                authWindow.setVisible(true);

                if (!authWindow.isCanceled()) {
                    org.apache.http.auth.Credentials credentials = new UsernamePasswordCredentials(authWindow.getUserName(),
                            new String(authWindow.getPassword()));

                    authUsername = authWindow.getUserName();
                    authPassword = new String(authWindow.getPassword());

                    // Save passwords if password remember is checked, if they seem not correct later will be removed.
                    if (authWindow.isRememberPassword()) {
                        HttpUtilActivator.getConfigurationService()
                                .setProperty(usernamePropertyName, authWindow.getUserName());
                        HttpUtilActivator.getCredentialsService()
                                .storePassword(passwordPropertyName, new String(authWindow.getPassword()));
                    }
                    return credentials;
                }

                // User canceled credentials input stop retry asking him if credentials are not correct
                retry = false;
            }
            else {
                // we have saved values lets return them
                authUsername = HttpUtilActivator.getConfigurationService().getString(usernamePropertyName);
                return new UsernamePasswordCredentials(authUsername, authPassword);
            }
            return null;
        }

        /**
         * Clear saved password. Used when we are in a situation that
         * saved username and password are no longer valid.
         */
        public void clear()
        {
            if (usedScope != null) {
                if (passwordPropertyName == null)
                    passwordPropertyName = getCredentialProperty(usedScope, PASSWORD);
                if (usernamePropertyName == null)
                    usernamePropertyName = getCredentialProperty(usedScope, USER_NAME);

                HttpUtilActivator.getConfigurationService().removeProperty(usernamePropertyName);
                HttpUtilActivator.getCredentialsService().removePassword(passwordPropertyName);
            }
            authUsername = null;
            authPassword = null;
            errorMessage = null;
        }

        /**
         * Constructs property name for save if one is not specified.
         * Its in the form HTTP_CREDENTIALS_PREFIX.host.realm.port
         *
         * @param authscope the scope, holds host,realm, port info about the host we are reaching.
         * @return return the constructed property.
         */
        private static String getCredentialProperty(AuthScope authscope)
        {
            String pref = HTTP_CREDENTIALS_PREFIX + authscope.getHost() +
                    "." + authscope.getRealm() +
                    "." + authscope.getPort();
            return pref;
        }

        /**
         * Constructs property name for saving if one is not specified.
         * It's in the form HTTP_CREDENTIALS_PREFIX.host.realm.port
         *
         * @param authScope the scope, holds host,realm, port info about the host we are reaching.
         * @return return the constructed property.
         */
        private static String getCredentialProperty(AuthScope authScope, String propertyName)
        {
            String pref = HTTP_CREDENTIALS_PREFIX + authScope.getHost() +
                    "." + authScope.getRealm() +
                    "." + authScope.getPort() +
                    "." + propertyName;
            return pref;
        }

        /**
         * Whether we need to continue retrying.
         *
         * @return whether we need to continue retrying.
         */
        boolean retry()
        {
            return retry;
        }

        /**
         * Returns authentication username if any
         *
         * @return authentication username or null
         */
        public String getAuthenticationUsername()
        {
            return authUsername;
        }

        /**
         * Returns authentication password if any
         *
         * @return authentication password or null
         */
        public String getAuthenticationPassword()
        {
            return authPassword;
        }
    }

    /**
     * Utility class wraps the http requests result and some utility methods for retrieving info and content for the result.
     */
    public static class HTTPResponseResult
    {
        /**
         * The httpclient entity.
         */
        ResponseBody mRespondBody;

        /**
         * The httpclient.
         */
        OkHttpClient httpClient;

        /**
         * Creates HTTPResponseResult.
         *
         * @param responseBody the httpclient responseBody.
         * @param httpClient the httpclient.
         */
        HTTPResponseResult(ResponseBody responseBody, OkHttpClient httpClient)
        {
            this.mRespondBody = responseBody;
            this.httpClient = httpClient;
        }

        /**
         * Tells the length of the content, if known.
         *
         * @return the number of bytes of the content, or a negative number if unknown. If the content length
         * is known but exceeds {@link Long#MAX_VALUE Long.MAX_VALUE}, a negative number is returned.
         */
        public long getContentLength()
        {
            return mRespondBody.contentLength();
        }

        /**
         * Returns a content stream of the entity.
         *
         * @return content stream of the entity.
         * @throws IOException if the stream could not be created
         * @throws IllegalStateException if content stream cannot be created.
         */
        public InputStream getContent()
                throws IOException, IllegalStateException
        {
            return mRespondBody.byteStream();
        }

        /**
         * Returns a content string of the entity.
         *
         * @return content string of the entity.
         * @throws IOException if the stream could not be created
         */
        public String getContentString()
                throws IOException
        {
            try {
                return mRespondBody.string();
            } catch (ParseException e) {
                e.printStackTrace();
            }
            return null;
        }

        /**
         * Get the credentials used by the request.
         *
         * @return the credentials (login at index 0 and password at index 1)
         */
        public String[] getCredentials()
        {
            String[] cred = new String[2];

            if (httpClient != null) {
                HTTPCredentialsProvider prov = (HTTPCredentialsProvider) HttpClientContext.create().getCredentialsProvider();
                cred[0] = prov.getAuthenticationUsername();
                cred[1] = prov.getAuthenticationPassword();
            }
            return cred;
        }
    }
}
