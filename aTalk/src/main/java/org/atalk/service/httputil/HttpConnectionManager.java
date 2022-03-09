package org.atalk.service.httputil;

import android.os.Build;

import org.apache.http.conn.ssl.StrictHostnameVerifier;
import org.atalk.android.BuildConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import timber.log.Timber;

public class HttpConnectionManager // extends AbstractConnectionManager
{
    public static final Executor EXECUTOR = Executors.newFixedThreadPool(4);
    public static final OkHttpClient OK_HTTP_CLIENT;

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


    public static String getUserAgent()
    {
        return String.format("%s/%s", BuildConfig.APPLICATION_ID, BuildConfig.VERSION_NAME);
//        return String.format("%s/%s", System.getProperty(Version.PNAME_APPLICATION_NAME,
//                System.getProperty(Version.PNAME_APPLICATION_VERSION));
    }

    public static Proxy getProxy()
    {
        final InetAddress localhost;
        try {
            localhost = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        } catch (final UnknownHostException e) {
            throw new IllegalStateException(e);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(localhost, 9050));
        }
        else {
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(localhost, 8118));
        }
    }

    public static OkHttpClient buildHttpClient(final String url, int readTimeout)
    {
        final OkHttpClient.Builder builder = OK_HTTP_CLIENT.newBuilder()
                .callTimeout(10, TimeUnit.SECONDS) // default timeout for complete calls
                .writeTimeout(10, TimeUnit.SECONDS) // default write timeout for new connections
                .readTimeout(readTimeout, TimeUnit.SECONDS) // default read timeout for new connections
                .followRedirects(true) // follow requests redirects
                .followSslRedirects(true) // follow HTTP tp HTTPS redirects
                .retryOnConnectionFailure(true) // retry or not when a connectivity problem is encountered
                .proxy(getProxy());

        setupTrustManager(builder, url);
        return builder.build();
    }

    public static void setupTrustManager(final OkHttpClient.Builder builder, String url)
    {
        try {
            final X509TrustManager trustManager =
                    HttpUtilActivator.getCertificateVerificationService().getTrustManager(url);
            final SSLSocketFactory sf = (SSLSocketFactory) SSLSocketFactory.getDefault();
            builder.sslSocketFactory(sf, trustManager);
            builder.hostnameVerifier(new StrictHostnameVerifier());
        } catch (final GeneralSecurityException ignored) {
        }
    }

    public static InputStream open(final String url, final boolean tor) throws IOException
    {
        return open(HttpUrl.get(url), tor);
    }

    public static InputStream open(final HttpUrl httpUrl, final boolean tor) throws IOException
    {
        final OkHttpClient.Builder builder = OK_HTTP_CLIENT.newBuilder();
        if (tor) {
            builder.proxy(getProxy()).build();
        }
        final OkHttpClient client = builder.build();
        final Request request = new Request.Builder().get().url(httpUrl).build();
        final ResponseBody body = client.newCall(request).execute().body();
        if (body == null) {
            // throw new IOException("No response body found");
            Timber.e("No response body found: %s", httpUrl);
            return null;
        }
        return body.byteStream();
    }
}
