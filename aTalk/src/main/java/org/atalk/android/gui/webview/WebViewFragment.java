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

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
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

import org.atalk.android.BuildConfig;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.service.osgi.OSGiFragment;
import org.jetbrains.annotations.NotNull;

import timber.log.Timber;

/**
 * The class displays the content accessed via given web link
 * https://developer.android.com/guide/webapps/webview
 *
 * @author Eng Chong Meng
 */
@SuppressLint("SetJavaScriptEnabled")
public class WebViewFragment extends OSGiFragment implements OnKeyListener {
    private WebView webview;
    private ProgressBar progressbar;
    private static final Stack<String> urlStack = new Stack<>();

    // stop webView.goBack() once we have started reload from urlStack
    private boolean isLoadFromStack = false;

    private String webUrl = null;
    private ValueCallback<Uri[]> mUploadMessageArray;

    @SuppressLint("JavascriptInterface")
    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View contentView = inflater.inflate(R.layout.webview_main, container, false);
        progressbar = contentView.findViewById(R.id.progress);
        progressbar.setIndeterminate(true);

        webview = contentView.findViewById(R.id.webview);
        final WebSettings webSettings = webview.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);

        // https://developer.android.com/guide/webapps/webview#BindingJavaScript
        webview.addJavascriptInterface(aTalkApp.getInstance(), "Android");
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setAllowUniversalAccessFromFileURLs(true);

        ActivityResultLauncher<String> mGetContents = getFileUris();
        webview.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                progressbar.setProgress(progress);
                if (progress < 100 && progress > 0 && progressbar.getVisibility() == ProgressBar.GONE) {
                    progressbar.setIndeterminate(true);
                    progressbar.setVisibility(ProgressBar.VISIBLE);
                }
                if (progress == 100) {
                    progressbar.setVisibility(ProgressBar.GONE);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> uploadMessageArray,
                    FileChooserParams fileChooserParams) {
                if (mUploadMessageArray != null)
                    mUploadMessageArray.onReceiveValue(null);

                mUploadMessageArray = uploadMessageArray;
                mGetContents.launch("*/*");
                return true;
            }
        });

        // https://developer.android.com/guide/webapps/webview#HandlingNavigation
        webview.setWebViewClient(new MyWebViewClient(this));

        // init webUrl with urlStack.pop() if non-empty, else load from default in DB
        if (urlStack.isEmpty()) {
            webUrl = ConfigurationUtils.getWebPage();
            urlStack.push(webUrl);
        }
        else {
            webUrl = urlStack.pop();
        }
        webview.loadUrl(webUrl);
        return contentView;
    }

    @Override
    public void onResume() {
        super.onResume();

        // setup keyPress listener - must re-enable every time on resume
        webview.setFocusableInTouchMode(true);
        webview.requestFocus();
        webview.setOnKeyListener(this);
    }

    /**
     * Init webView so it download root url stored in DB on next init
     */
    public static void initWebView() {
        urlStack.clear();
    }

    /**
     * Push the last loaded/user clicked url page to the urlStack for later retrieval in onCreateView(),
     * allow same web page to be shown when user slides and returns to the webView
     *
     * @param url loaded/user clicked url
     */
    public void addLastUrl(String url) {
        urlStack.push(url);
        isLoadFromStack = false;
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

    // Prevent the webView from reloading on device rotation
    @Override
    public void onConfigurationChanged(@NotNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    public static Bitmap getBitmapFromURL(String src) {
        try {
            URL url = new URL(src);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            return BitmapFactory.decodeStream(input);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Handler for user enter Back Key
     * User Back Key entry will return to previous web access pages until root; before return to caller
     *
     * @param v view
     * @param keyCode the entered key keycode
     * @param event the key Event
     *
     * @return true if process
     */
    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            // android OS will not pass in KEYCODE_MENU???
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                webview.loadUrl("javascript:MovimTpl.toggleMenu()");
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (!isLoadFromStack && webview.canGoBack()) {
                    // Remove the last saved/displayed url push in addLastUrl, so an actual previous page is shown
                    if (!urlStack.isEmpty())
                        urlStack.pop();
                    webview.goBack();
                    return true;
                }
                // else continue to reload url from urlStack if non-empty.
                else if (!urlStack.isEmpty()) {
                    isLoadFromStack = true;
                    webUrl = urlStack.pop();
                    Timber.w("urlStack pop(): %s", webUrl);
                    webview.loadUrl(webUrl);
                    return true;
                }
            }
        }
        return false;
    }
}