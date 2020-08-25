/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.service.httputil;

import net.java.sip.communicator.service.gui.AuthenticationWindowService;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.auth.*;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.entity.mime.FileBody;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.*;
import org.apache.hc.client5.http.impl.classic.*;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.RedirectStrategy;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.net.URLEncodedUtils;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;

import java.io.*;
import java.net.*;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;

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

    /**
     * Maximum number of http redirects (301, 302, 303).
     */
    private static final int MAX_REDIRECTS = 10;

    /*
     * Local global variables use in getHttpClient() and in other methods if required;
     */
    private static HTTPCredentialsProvider credentialsProvider;

    private static PoolingHttpClientConnectionManager connectionManager = null;

    /**
     * Opens a connection to the <tt>uri</tt>.
     *
     * @param uri the HTTP uri to contact.
     * @return the result if any or null if connection was not possible or canceled by user.
     */
    public static HTTPResponseResult openURLConnection(String uri)
    {
        return openURLConnection(uri, null, null, null, null);
    }

    /**
     * Opens a connection to the <tt>uri</tt>.
     *
     * @param uri the HTTP uri to contact.
     * @param headerParamNames additional header name to include
     * @param headerParamValues corresponding header value to include
     * @return the result if any or null if connection was not possible or canceled by user.
     */
    public static HTTPResponseResult openURLConnection(String uri, String[] headerParamNames, String[] headerParamValues)
    {
        return openURLConnection(uri, null, null, headerParamNames, headerParamValues);
    }

    /**
     * Opens a connection to the <tt>uri</tt>.
     *
     * @param uri the HTTP uri to contact.
     * @param usernamePropertyName the property uses to retrieve/store username value
     * if protected site is hit; for username retrieval, the ConfigurationService service is used.
     * @param passwordPropertyName the property uses to retrieve/store password value
     * if protected site is hit; for password retrieval, the  CredentialsStorageService service is used.
     * @param headerParamNames additional header name to include
     * @param headerParamValues corresponding header value to include
     * @return the result if any or null if connection was not possible or canceled by user.
     */
    public static HTTPResponseResult openURLConnection(String uri, String usernamePropertyName,
            String passwordPropertyName, String[] headerParamNames, String[] headerParamValues)
    {
        try {
            HttpGet httpGet = new HttpGet(uri);
            CloseableHttpClient httpClient = getHttpClient(httpGet.getUri().getHost(),
                    new HTTPCredentialsProvider(usernamePropertyName, passwordPropertyName), null);

            /* add additional HTTP header */
            if ((headerParamNames != null) && (headerParamValues != null)) {
                for (int i = 0; i < headerParamNames.length; i++) {
                    httpGet.addHeader(new BasicHeader(headerParamNames[i], headerParamValues[i]));
                }
            }
            HttpEntity result = executeMethod(httpClient, httpGet, null, null);
            if (result == null)
                return null;

            return new HTTPResponseResult(result, httpClient);
        } catch (Throwable t) {
            Timber.e("Cannot open connection to %s: %s", uri, t.getMessage());
        }
        return null;
    }

    /**
     * Executes the method and return the result. Handle ask for password when hitting password protected site.
     * Keep asking for password till user clicks cancel or enters correct password.
     * When 'remember password' is checked password is saved, if this
     * password and username are not correct clear them, if there are correct they stay saved.
     *
     * @param httpClient the configured http client to use.
     * @param req the request for now it is get or post.
     * @param redirectHandler handles redirection, should we redirect and the actual redirect.
     * @param parameters if we are redirecting we can use already filled
     * username and password in order to avoid asking the user twice.
     * @return the result http entity.
     */
    private static HttpEntity executeMethod(CloseableHttpClient httpClient, HttpUriRequestBase req,
            RedirectHandler redirectHandler, List<NameValuePair> parameters)
            throws Throwable
    {
        // do it when response (first execution) or till we are unauthorized
        CloseableHttpResponse response = null;
        int redirects = 0;
        while (response == null
                || (HttpStatus.SC_UNAUTHORIZED == response.getCode())
                || (HttpStatus.SC_FORBIDDEN == response.getCode())) {
            // if we were unauthorized, lets clear the method and recreate it
            // for new connection with new credentials.
            if ((response != null)
                    && ((HttpStatus.SC_UNAUTHORIZED == response.getCode())
                    || (HttpStatus.SC_FORBIDDEN == response.getCode()))) {
                Timber.d("Will retry http connect and credentials input as latest are not correct!");
                throw new AuthenticationException("Authorization needed");
            }
            else {
                Timber.i("Auto checking for software update: %s", req);
                response = httpClient.execute(req);
            }

            // if user click cancel no need to retry, stop trying
            if (!(credentialsProvider.retry())) {
                Timber.d("User canceled credentials input.");
                break;
            }

            // check for post redirect as post redirects are not handled automatically CloseableHttpClient???)
            // RFC2616 (10.3 Redirection 3xx). The second request (forwarded method) can only be a GET or HEAD.
            Header locationHeader = response.getFirstHeader("location");
            if ((locationHeader != null) && req instanceof HttpPost
                    && ((HttpStatus.SC_MOVED_PERMANENTLY == response.getCode())
                    || (HttpStatus.SC_MOVED_TEMPORARILY == response.getCode())
                    || (HttpStatus.SC_SEE_OTHER == response.getCode()))
                    && redirects < MAX_REDIRECTS) {
                HttpUriRequestBase oldreq = req;
                oldreq.abort();

                // lets ask redirection handler if any (cmeng: handle within
                String newLocation = locationHeader.getValue();
                if ((redirectHandler != null)
                        && redirectHandler.handleRedirect(newLocation, parameters)) {
                    return null;
                }

                req = new HttpGet(newLocation);
                req.setConfig(oldreq.getConfig());
                req.setHeaders(oldreq.getHeaders());
                redirects++;
                response = httpClient.execute(req);
            }
        }

        // if we finally managed to login return the result.
        if ((response != null)
                && HttpStatus.SC_OK == response.getCode()) {
            return response.getEntity();
        }

        // is user has canceled no result needed.
        return null;
    }

    /**
     * Posts a <tt>file</tt> to the <tt>uri</tt>.
     *
     * @param uri the HTTP uri to post the form to.
     * @param fileParamName the name of the param for the file.
     * @param file the file we will send.
     * @return the result or null if send was not possible or credentials ask if any was canceled.
     */
    public static HTTPResponseResult postFile(String uri, String fileParamName, File file)
    {
        return postFile(uri, fileParamName, file, null, null);
    }

    /**
     * Posts a <tt>file</tt> to the <tt>uri</tt>.
     *
     * @param uri the HTTP uri to post the form to.
     * @param fileParamName the name of the param for the file.
     * @param file the file we will send.
     * @param usernamePropertyName the property uses to retrieve/store username value
     * if protected site is hit; for username retrieval, the ConfigurationService service is used.
     * @param passwordPropertyName the property uses to retrieve/store password value
     * if protected site is hit, for password retrieval, the  CredentialsStorageService service is used.
     * @return the result or null if send was not possible or credentials ask if any was canceled.
     */
    public static HTTPResponseResult postFile(String uri, String fileParamName, File file,
            String usernamePropertyName, String passwordPropertyName)
    {
        CloseableHttpClient httpClient;
        try {
            HttpPost postMethod = new HttpPost(uri);
            httpClient = getHttpClient(postMethod.getUri().getHost(),
                    new HTTPCredentialsProvider(usernamePropertyName, passwordPropertyName), null);

            String mimeType = URLConnection.guessContentTypeFromName(file.getPath());
            if (mimeType == null)
                mimeType = "application/octet-stream";

            FileBody fileBody = new FileBody(file, ContentType.create(mimeType));
            // MultipartEntity reqEntity = new MultipartEntity();
            // reqEntity.addPart(fileParamName, fileBody);

            HttpEntity reqEntity = MultipartEntityBuilder.create()
                    .addPart(fileParamName, fileBody)
                    .build();

            postMethod.setEntity(reqEntity);
            HttpEntity resEntity = executeMethod(httpClient, postMethod, null, null);

            if (resEntity == null)
                return null;
            return new HTTPResponseResult(resEntity, httpClient);
        } catch (Throwable e) {
            Timber.e(e, "Cannot post file to:%s", uri);
        }
        return null;
    }

    /**
     * Posting form to HTTP <tt>uri</tt>. For submission we use POST method
     * which is "application/x-www-form-urlencoded" encoded.
     *
     * @param uri HTTP uri.
     * @param usernamePropertyName the property uses to retrieve/store username value
     * if protected site is hit; for username retrieval, the ConfigurationService service is used.
     * @param passwordPropertyName the property uses to retrieve/store password value
     * if protected site is hit, for password retrieval, the  CredentialsStorageService service is used.
     * @param formParamNames the parameter names to include in post.
     * @param formParamValues the corresponding parameter values to use.
     * @param usernameParamIx the index of the username parameter in the
     * <tt>formParamNames</tt> and <tt>formParamValues</tt> if any, otherwise -1.
     * @param passwordParamIx the index of the password parameter in the
     * <tt>formParamNames</tt> and <tt>formParamValues</tt> if any, otherwise -1.
     * @return the result or null if send was not possible or credentials ask if any was canceled.
     */
    public static HTTPResponseResult postForm(String uri, String usernamePropertyName, String passwordPropertyName,
            ArrayList<String> formParamNames, ArrayList<String> formParamValues, int usernameParamIx, int passwordParamIx)
            throws Throwable
    {
        return postForm(uri, usernamePropertyName, passwordPropertyName, formParamNames, formParamValues,
                usernameParamIx, passwordParamIx, null);
    }

    /**
     * Posting form to <tt>uri</tt>. For submission we use POST method
     * which is "application/x-www-form-urlencoded" encoded.
     *
     * @param uri HTTP uri.
     * @param usernamePropertyName the property uses to retrieve/store username value
     * if protected site is hit; for username retrieval, the ConfigurationService service is used.
     * @param passwordPropertyName the property uses to retrieve/store password value
     * if protected site is hit, for password retrieval, the  CredentialsStorageService service is used.
     * @param formParamNames the parameter names to include in post.
     * @param formParamValues the corresponding parameter values to use.
     * @param usernameParamIx the index of the username parameter in the
     * <tt>formParamNames</tt> and <tt>formParamValues</tt> if any, otherwise -1.
     * @param passwordParamIx the index of the password parameter in the
     * <tt>formParamNames</tt> and <tt>formParamValues</tt> if any, otherwise -1.
     * @param redirectHandler handles redirection, should we redirect and the actual redirect.
     * @return the result or null if send was not possible or credentials ask if any was canceled.
     */
    public static HTTPResponseResult postForm(String uri, String usernamePropertyName, String passwordPropertyName,
            ArrayList<String> formParamNames, ArrayList<String> formParamValues,
            int usernameParamIx, int passwordParamIx, RedirectHandler redirectHandler)
            throws Throwable
    {
        return postForm(uri, usernamePropertyName, passwordPropertyName, formParamNames, formParamValues,
                usernameParamIx, passwordParamIx, redirectHandler, null, null);
    }

    /**
     * Posting form to <tt>uri</tt>. For submission we use POST method
     * which is "application/x-www-form-urlencoded" encoded.
     *
     * @param uri HTTP uri.
     * @param headerParamNames additional header name to include
     * @param headerParamValues corresponding header value to include
     * @return the result or null if send was not possible or credentials ask if any was canceled.
     */
    public static HTTPResponseResult postForm(String uri, List<String> headerParamNames, List<String> headerParamValues)
            throws Throwable
    {
        return postForm(uri, null, null, null, null,
                -1, -1, null, headerParamNames, headerParamValues);
    }

    /**
     * Posting form to <tt>uri</tt>. For submission we use POST method
     * which is "application/x-www-form-urlencoded" encoded.
     *
     * @param uri HTTP uri.
     * @param usernamePropertyName the property uses to retrieve/store username value
     * if protected site is hit; for username retrieval, the ConfigurationService service is used.
     * @param passwordPropertyName the property uses to retrieve/store password value
     * if protected site is hit, for password retrieval, the  CredentialsStorageService service is used.
     * @param formParamNames the parameter names to include in post.
     * @param formParamValues the corresponding parameter values to use.
     * @param usernameParamIx the index of the username parameter in the
     * <tt>formParamNames</tt> and <tt>formParamValues</tt> if any, otherwise -1.
     * @param passwordParamIx the index of the password parameter in the
     * <tt>formParamNames</tt> and <tt>formParamValues</tt> if any, otherwise -1.
     * @param redirectHandler handles redirection, should we redirect and the actual redirect.
     * @param headerParamNames additional header name to include
     * @param headerParamValues corresponding header value to include
     * @return the result or null if send was not possible or credentials ask if any was canceled.
     */
    // @TODO cmeng: the next two methods need major cleanup
    public static HTTPResponseResult postForm(String uri, String usernamePropertyName, String passwordPropertyName,
            ArrayList<String> formParamNames, ArrayList<String> formParamValues, int usernameParamIx, int passwordParamIx,
            RedirectHandler redirectHandler, List<String> headerParamNames, List<String> headerParamValues)
            throws Throwable
    {
        CloseableHttpClient httpClient;
        HttpPost postMethod;
        HttpEntity resEntity = null;

        credentialsProvider = new HTTPCredentialsProvider(usernamePropertyName, passwordPropertyName);

        // if we have username and password in the parameters, lets retrieve their values
        // if there are already filled skip asking the user
        Credentials credentials = null;
        if (usernameParamIx != -1
                && usernameParamIx < formParamNames.size()
                && passwordParamIx != -1
                && passwordParamIx < formParamNames.size()
                && (formParamValues.get(usernameParamIx) == null
                || formParamValues.get(usernameParamIx).length() == 0)
                && (formParamValues.get(passwordParamIx) == null
                || formParamValues.get(passwordParamIx).length() == 0)) {
            URL url = new URL(uri);

            // don't allow empty username
            while (credentials == null
                    || credentials.getUserPrincipal() == null
                    || StringUtils.isEmpty(credentials.getUserPrincipal().getName())) {
                credentials = credentialsProvider.getCredentials(new AuthScope(url.getHost(), url.getPort()), null);

                // it was user canceled lets stop processing
                if (credentials == null && !credentialsProvider.retry()) {
                    return null;
                }
            }
        }

        // construct the name value pairs we will be sending
        List<NameValuePair> parameters = new ArrayList<>();
        // there can be no params
        if (formParamNames != null) {
            for (int i = 0; i < formParamNames.size(); i++) {
                // we are on the username index, insert retrieved username value
                if (i == usernameParamIx && credentials != null) {
                    parameters.add(new BasicNameValuePair(formParamNames.get(i), credentials.getUserPrincipal().getName()));
                }
                // we are on the password index, insert retrieved password val
                else if (i == passwordParamIx && credentials != null) {
                    parameters.add(new BasicNameValuePair(formParamNames.get(i), new String(credentials.getPassword())));
                }
                // common name value pair, all info is present
                else {
                    parameters.add(new BasicNameValuePair(formParamNames.get(i), formParamValues.get(i)));
                }
            }
        }

        // Custom strategy: check redirect handler for redirect, if missing will use the default handler
        CustomRedirectStrategy customRedirectStrategy = new CustomRedirectStrategy(redirectHandler, parameters);

        // if any authentication exception rise while executing will retry
        AuthenticationException authEx;
        do {
            postMethod = new HttpPost(uri);
            httpClient = getHttpClient(postMethod.getUri().getHost(), credentialsProvider, customRedirectStrategy);

            try {
                // execute post
                resEntity = postForm(httpClient, postMethod, redirectHandler, parameters, headerParamNames, headerParamValues);

                authEx = null;
            } catch (AuthenticationException ex) {
                authEx = ex;
                // let's reuse credentialsProvider and show the same username
                String userName = credentialsProvider.authUsername;
                credentialsProvider.authUsername = userName;
                credentialsProvider.errorMessage = aTalkApp.getResString(R.string.service_gui_AUTHENTICATION_FAILED,
                        credentialsProvider.usedScope.getHost());
            }
        }
        while (authEx != null);

        // canceled or no result = (resEntity == null)
        return (resEntity == null) ? null : new HTTPResponseResult(resEntity, httpClient);
    }

    /**
     * Posting form to HTTP <tt>uri</tt>. For submission we use POST method
     * which is "application/x-www-form-urlencoded" encoded.
     *
     * @param httpClient the http client
     * @param postMethod the post method
     * @param redirectHandler handles redirection, should we redirect and the actual redirect.
     * @param formParamNames the parameter names to include in post.
     * @param formParamValues the corresponding parameter values to use.
     * @param usernameParamIx the index of the username parameter in the
     * <tt>formParamNames</tt> and <tt>formParamValues</tt> if any, otherwise -1.
     * @param passwordParamIx the index of the password parameter in the
     * <tt>formParamNames</tt> and <tt>formParamValues</tt> if any, otherwise -1.
     * @param headerParamNames additional header name to include
     * @param headerParamValues corresponding header value to include
     * @return the result or null if send was not possible or credentials ask if any was canceled.
     */
    private static HttpEntity postForm(CloseableHttpClient httpClient, HttpPost postMethod, RedirectHandler redirectHandler,
            List<NameValuePair> parameters, List<String> headerParamNames, List<String> headerParamValues)
            throws Throwable
    {
        // Uses String UTF-8 to keep compatible with android version and
        // older versions of the http client libs, as the one used in debian (4.1.x)
        String s = URLEncodedUtils.format(parameters, ContentType.APPLICATION_FORM_URLENCODED.getCharset());
        StringEntity entity = new StringEntity(s, ContentType.APPLICATION_FORM_URLENCODED);

        // insert post values encoded.
        postMethod.setEntity(entity);

        if (headerParamNames != null) {
            for (int i = 0; i < headerParamNames.size(); i++) {
                postMethod.addHeader(headerParamNames.get(i), headerParamValues.get(i));
            }
        }
        // execute post
        return executeMethod(httpClient, postMethod, redirectHandler, parameters);
    }

    /**
     * Returns the preconfigured http client,
     * using CertificateVerificationService, timeouts, user-agent,
     * hostname verifier, proxy settings are used from global java settings,
     * if protected site is hit asks for credentials
     * using util.swing.AuthenticationWindow.
     *
     * @param usernamePropertyName the property uses to retrieve/store username value
     * if protected site is hit; for username retrieval, the ConfigurationService service is used.
     * @param passwordPropertyName the property uses to retrieve/store password value
     * if protected site is hit, for password retrieval, the  CredentialsStorageService service is used.
     * @param credentialsProvider if not null provider will bre reused in the new client
     * @param uri the HTTP uri we will be connecting to
     */
    public static CloseableHttpClient getHttpClient(final String uri,
            CredentialsProvider credentialsProvider, RedirectStrategy redirectStrategy)
            throws IOException
    {
//        BasicHttpParams params = new BasicHttpParams();
//        params.setParameter(CoreConnectionPNames.SO_TIMEOUT, 10000);
//        params.setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 10000);
//        params.setParameter(ClientPNames.MAX_REDIRECTS, MAX_REDIRECTS);
//
//        HttpProtocolParams.setUserAgent(httpClient.getParams(),
//                System.getProperty("sip-communicator.application.name")
//                        + "/" + System.getProperty("sip-communicator.version"));

        SSLContext sslContext = null;
        try {
            sslContext = HttpUtilActivator.getCertificateVerificationService()
                    .getSSLContext(HttpUtilActivator.getCertificateVerificationService().getTrustManager(uri));
        } catch (GeneralSecurityException e) {
            throw new IOException(e.getMessage());
        }

        HttpClientBuilder hcBuilder = HttpClientBuilder.create().useSystemProperties()
                .setConnectionManager(getConnectionManager(sslContext))
                .setDefaultCredentialsProvider(credentialsProvider)
                .setUserAgent(System.getProperty("sip-communicator.application.name")
                        + "/" + System.getProperty("sip-communicator.version"));


        // note to any reviewer concerned about ALLOW_ALL_HOSTNAME_VERIFIER: the SSL
        // context obtained from the certificate service takes care of certificate validation
//        try {
//            Scheme sch = new Scheme("https", 443, new SSLSocketFactoryEx(sslCtx));
//            httpClient.getConnectionManager().getSchemeRegistry().register(sch);
//        } catch (Throwable t) {
//            Timber.e(t, "Error creating ssl socket factory");
//        }

        // set proxy from default jre settings
//        ProxySelectorRoutePlanner routePlanner = new ProxySelectorRoutePlanner(
//                httpClient.getConnectionManager().getSchemeRegistry(), ProxySelector.getDefault());

        // our custom strategy, will check redirect handler should we redirect if missing will use the default handler
        if (redirectStrategy != null) {
            hcBuilder.setRedirectStrategy(redirectStrategy);
        }
        else {
            hcBuilder.setRedirectStrategy(DefaultRedirectStrategy.INSTANCE);
        }

        SchemePortResolver schemePortResolver = DefaultSchemePortResolver.INSTANCE;
        HttpRoutePlanner routePlanner = new SystemDefaultRoutePlanner(schemePortResolver, ProxySelector.getDefault());
        hcBuilder.setRoutePlanner(routePlanner);

        // enable retry connecting with default retry handler when connecting has prompted for authentication
        // connection can be disconnected before user answers and we need to retry connection, using the credentials provided
        hcBuilder.setRetryStrategy(new DefaultHttpRequestRetryStrategy(3, TimeValue.ofSeconds(1L)));

        return hcBuilder.build();
    }

    public static PoolingHttpClientConnectionManager getConnectionManager(SSLContext sslContext)
    {
        if (connectionManager != null)
            return connectionManager;

        if (sslContext == null)
            sslContext = SSLContexts.createSystemDefault();

        connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(SSLConnectionSocketFactoryBuilder.create()
                        .setSslContext(sslContext)
                        .setTlsVersions(TLS.V_1_3, TLS.V_1_2)
                        .build())
                .setDefaultSocketConfig(SocketConfig.custom()
                        .setSoTimeout(Timeout.ofSeconds(5))
                        .build())
                .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.STRICT)
                .setConnPoolPolicy(PoolReusePolicy.LIFO)
                .setConnectionTimeToLive(TimeValue.ofMinutes(1L))
                .build();

        return connectionManager;
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
         * Get the {@link org.apache.hc.client5.http.auth.Credentials Credentials} for the given authentication scope.
         *
         * @param authScope the {@link org.apache.hc.client5.http.auth.AuthScope authentication scope}
         * @return the credentials
         * @see #setCredentials(org.apache.hc.client5.http.auth.AuthScope, org.apache.hc.client5.http.auth.Credentials)
         */
        @Override
        public Credentials getCredentials(AuthScope authScope, HttpContext context)
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
                    Credentials credentials
                            = new UsernamePasswordCredentials(authWindow.getUserName(), authWindow.getPassword());

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
                return new UsernamePasswordCredentials(authUsername, authPassword.toCharArray());
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
     * Input stream wrapper which handles closing the httpclient when everything is retrieved.
     */
    private static class HttpClientInputStream extends InputStream
    {
        /**
         * The original input stream.
         */
        InputStream in;

        /**
         * The http client to close.
         */
        CloseableHttpClient httpClient;

        /**
         * Creates HttpClientInputStream.
         *
         * @param in the original input stream.
         * @param httpClient the http client to close.
         */
        HttpClientInputStream(InputStream in, CloseableHttpClient httpClient)
        {
            this.in = in;
            this.httpClient = httpClient;
        }

        /**
         * Uses parent InputStream read method.
         *
         * @return the next byte of data, or {@code -1} if the end of the stream is reached.
         * @throws IOException if an I/O error occurs.
         */
        @Override
        public int read()
                throws IOException
        {
            return in.read();
        }

        /**
         * Closes this input stream and releases any system resources associated
         * with the stream. Releases httpclient connections.
         *
         * The {@code close} method of {@code InputStream} does nothing.
         *
         * @throws IOException if an I/O error occurs.
         */
        @Override
        public void close()
                throws IOException
        {
            super.close();

            // When HttpClient instance is no longer needed, shut down the connection manager to ensure
            // immediate de-allocation of all system resources
            connectionManager.close();
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
        HttpEntity entity;

        /**
         * The httpclient.
         */
        CloseableHttpClient httpClient;

        /**
         * Creates HTTPResponseResult.
         *
         * @param entity the httpclient entity.
         * @param httpClient the httpclient.
         */
        HTTPResponseResult(HttpEntity entity, CloseableHttpClient httpClient)
        {
            this.entity = entity;
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
            return entity.getContentLength();
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
            return new HttpClientInputStream(entity.getContent(), httpClient);
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
                try {
                    return EntityUtils.toString(entity);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            } finally {
                if (connectionManager != null)
                    connectionManager.close();
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
                HTTPCredentialsProvider prov
                        = (HTTPCredentialsProvider) HttpClientContext.create().getCredentialsProvider();
                cred[0] = prov.getAuthenticationUsername();
                cred[1] = prov.getAuthenticationPassword();
            }
            return cred;
        }
    }

    /**
     * Custom redirect handler that extends DefaultRedirectStrategy
     * We will check redirect handler should we redirect
     * If redirect handler is missing will continue with default strategy
     */
    private static class CustomRedirectStrategy extends DefaultRedirectStrategy
    {
        /**
         * The redirect handler to check.
         */
        private final RedirectHandler handler;

        /**
         * The already filled parameters to be used when redirecting.
         */
        private final List<NameValuePair> parameters;

        /**
         * Created custom redirect strategy.
         *
         * @param handler the redirect handler.
         * @param parameters already filled parameters.
         */
        CustomRedirectStrategy(RedirectHandler handler, List<NameValuePair> parameters)
        {
            this.handler = handler;
            this.parameters = parameters;
        }

        /**
         * Check whether we need to redirect.
         *
         * @param request the initial request
         * @param response the response containing the location param for redirect.
         * @param context the http context.
         * @return should we redirect.
         */
        @Override
        public boolean isRedirected(final HttpRequest request, final HttpResponse response, final HttpContext context)
                throws ProtocolException
        {
            Header locationHeader = response.getFirstHeader("location");
            if ((handler != null)
                    && (locationHeader != null)
                    && handler.hasParams(locationHeader.getValue())) {
                //we will cancel this redirect and will schedule new redirect
                handler.handleRedirect(locationHeader.getValue(), parameters);
                return false;
            }
            return super.isRedirected(request, response, context);
        }
    }

    /**
     * The redirect handler will cancel/proceed the redirection. Can
     * schedule new request with the redirect location, reusing the already filled parameters.
     */
    public interface RedirectHandler
    {
        /**
         * Schedule new request with the redirect location, reusing the already filled parameters.
         *
         * @param location the new location.
         * @param parameters the parameters that were already filled.
         * @return should we continue with normal redirect.
         */
        boolean handleRedirect(String location, List<NameValuePair> parameters);

        /**
         * Do the new location has params that need to be filled, return <tt>true</tt> will cause it to handle redirect.
         *
         * @param location the new location.
         * @return <tt>true</tt> if we need to redirect in the handler or
         * <tt>false</tt> if we will continue with default redirect handling.
         */
        boolean hasParams(String location);
    }
}
