/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
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
package org.atalk.android.gui.webview;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ProgressBar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Stack;

import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.android.BaseFragment;
import org.atalk.android.BuildConfig;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.aTalk;

import org.jetbrains.annotations.NotNull;

import timber.log.Timber;

/**
 * The class displays the content accessed via given web link
 * <a href="https://developer.android.com/guide/webapps/webview">...</a>
 *
 * @author Eng Chong Meng
 */
@SuppressLint("SetJavaScriptEnabled")
public class WebViewFragment extends BaseFragment implements aTalk.OnBackPressedListener {
    private WebView webView;
    private static final Stack<String> urlStack = new Stack<>();
    private String webUrl = null;
    private ValueCallback<Uri[]> mUploadMessageArray;

    @SuppressLint("JavascriptInterface")
    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View contentView = inflater.inflate(R.layout.webview_main, container, false);
        final ProgressBar progressbar = contentView.findViewById(R.id.progress);
        progressbar.setIndeterminate(true);

        webView = contentView.findViewById(R.id.webview);
        final WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);

        // https://developer.android.com/guide/webapps/webview#BindingJavaScript
        webView.addJavascriptInterface(aTalkApp.getInstance(), "Android");
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setAllowUniversalAccessFromFileURLs(true);

        webView.setWebViewClient(new MyWebViewClient(this) {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressbar.setVisibility(View.INVISIBLE);
            }
        });
        return contentView;
    }

    @Override
    public void onResume() {
        super.onResume();

        // init webUrl with urlStack.pop() if non-empty, else load from default in DB
        if (urlStack.isEmpty()) {
            webUrl = ConfigurationUtils.getWebPage();
            urlStack.push(webUrl);
        }
        else {
            webUrl = urlStack.pop();
        }

        if (!TextUtils.isEmpty(webUrl))
            webView.loadUrl(webUrl);

        webView.setFocusableInTouchMode(true);
        webView.requestFocus();
    }

    /**
     * Init webView so it download root url stored in DB on next init
     */
    public static void initWebView() {
        urlStack.clear();
    }
    /**
     * Opens a FileChooserDialog to let the user pick files for upload
     */
    private ActivityResultLauncher<String> getFileUris() {
        return registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), uris -> {
            if (uris != null) {
                if (mUploadMessageArray == null)
                    return;

                Uri[] uriArray = new Uri[uris.size()];
                uriArray = uris.toArray(uriArray);

                mUploadMessageArray.onReceiveValue(uriArray);
                mUploadMessageArray = null;
            }
            else {
                aTalkApp.showToastMessage(R.string.file_does_not_exist);
            }
        });
    }

    public static Bitmap getBitmapFromURL(String src) {
        try {
            URL url = new URL(src);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            return BitmapFactory.decodeStream(input);
        }
        catch (IOException e) {
            Timber.w("Exception %s", e.getMessage());
            return null;
        }
    }

    /**
     * Handler for user enter Back Key in current webView.
     * If not consumed, user Back Key entry will return to previous fragment until root.
     *
     * @return true if consumed.
     */
    @Override
    public boolean onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        // Return false to let the activity or next handler handle it
        return false;
    }
}