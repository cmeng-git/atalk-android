package org.atalk.service.httputil;

import android.text.TextUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.http.conn.ssl.StrictHostnameVerifier;
import org.atalk.android.BuildConfig;
import org.json.JSONObject;

import okhttp3.Call;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import timber.log.Timber;

public class OkHttpUtils {
    public static final OkHttpClient OK_HTTP_CLIENT;
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    static {
        OK_HTTP_CLIENT = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    final Request original = chain.request();
                    final Request modified = original.newBuilder()
                            .header("User-Agent", getUserAgent())
                            .build();
                    return chain.proceed(modified);
                })
                .build();
    }

    public static String getUserAgent() {
        return String.format("%s/%s", BuildConfig.APPLICATION_ID, BuildConfig.VERSION_NAME);
//        return String.format("%s/%s", System.getProperty(Version.PNAME_APPL_NAME,
//                System.getProperty(Version.PNAME_APP_VERSION));
    }

    // Currently not implemented for proxy support; do not use.
    public static Proxy getProxy() {
        final InetAddress localhost;
        try {
            localhost = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        } catch (final UnknownHostException e) {
            throw new IllegalStateException(e);
        }
        return new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(localhost, 9050));
    }

    public static OkHttpClient buildHttpClient(final String url, int readTimeout) {
        final OkHttpClient.Builder builder = OK_HTTP_CLIENT.newBuilder()
                .callTimeout(10, TimeUnit.SECONDS) // default timeout for complete calls
                .writeTimeout(10, TimeUnit.SECONDS) // default write timeout for new connections
                .readTimeout(readTimeout, TimeUnit.SECONDS) // default read timeout for new connections
                .followRedirects(true) // follow requests redirects
                .followSslRedirects(true) // follow HTTP tp HTTPS redirects
                .retryOnConnectionFailure(true); // retry or not when a connectivity problem is encountered
        // .proxy(getProxy());

        setupTrustManager(builder, url);
        return builder.build();
    }

    // Trusting a Self-Signed Certificate in OkHttp
    public static void setupTrustManager(final OkHttpClient.Builder builder, String url) {
        try {
            final X509TrustManager trustManager = HttpUtilActivator.getCertificateVerificationService().getTrustManager(url);
            final SSLSocketFactory sf = (SSLSocketFactory) SSLSocketFactory.getDefault();
            builder.sslSocketFactory(sf, trustManager);
            builder.hostnameVerifier(new StrictHostnameVerifier());
        } catch (final GeneralSecurityException ignored) {
        }
    }

    public static ResponseBody open(final HttpUrl httpUrl, final boolean tor) throws IOException {
        final OkHttpClient.Builder builder = OK_HTTP_CLIENT.newBuilder();
        if (tor) {
            builder.proxy(getProxy()).build();
        }

        final OkHttpClient client = builder.build();
        final Request request = new Request.Builder()
                .get()
                .url(httpUrl)
                .build();

        ResponseBody respBody = null;
        try (Response response = client.newCall(request).execute()) {
            respBody = response.body();
        } catch (Exception e) {
            Timber.w("Exception: %s", e.getMessage());
        }

        if (respBody == null) {
            // throw new IOException("No response body found");
            Timber.e("No response body found: %s", httpUrl);
            return null;
        }
        return respBody;
    }

    /**
     * Posting form to <tt>url</tt>. For submission we use POST method i.e. "application/x-www-form-urlencoded" encoded.
     * By default, OkHttp automatically follows redirects, including 307s. This means that if you send a POST request
     * and get a 307 response, OkHttp will automatically resend the POST request to the new location.
     *
     * @param url HTTP address.
     * @param jsonObject the Json parameters to include in post if not null or empty.
     * @param username the username parameter if any for protected site, otherwise null
     * @param password the password parameter if any for protected site, otherwise null
     * @param headerParams additional header valuesPair to include
     *
     * @return the result or throws IOException
     */
    public static ResponseBody postForm(String url, JSONObject jsonObject, String username, String password,
            LinkedHashMap<String, String> headerParams)
            throws IOException {

        Request.Builder reqBuilder = new Request.Builder()
                .url(url);

        if (jsonObject != null && jsonObject.length() > 0) {
            String content = jsonObject.toString();
            RequestBody formBody = RequestBody.create (content, JSON);
            reqBuilder.post(formBody);
        }

        if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(password)) {
            String credential = Credentials.basic(username, password);
            reqBuilder.addHeader("Authorization", credential);
        }

        if (headerParams != null && !headerParams.isEmpty()) {
            for (Map.Entry<String, String> entry : headerParams.entrySet()) {
                reqBuilder.addHeader(entry.getKey(), entry.getValue());
            }
        }

        OkHttpClient httpClient = buildHttpClient(url, 10);
        Call call = httpClient.newCall(reqBuilder.build());
        // Must not use try close here as the data is yet to be processed by caller.
        Response response = call.execute();
        if (response.isSuccessful())
            return response.body();
        else if (response.isRedirect()) {
            Timber.d("HttpPost is redirect: %s", response.code());
        } else {
            Timber.d("HttpPost response: %s", response.networkResponse());
        }
        return null;
    }
}
