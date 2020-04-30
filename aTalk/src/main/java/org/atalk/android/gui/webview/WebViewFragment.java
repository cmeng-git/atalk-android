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
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.*;
import android.view.View.OnKeyListener;
import android.webkit.*;
import android.widget.ProgressBar;

import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.android.*;
import org.atalk.service.osgi.OSGiFragment;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import timber.log.Timber;

@SuppressLint("SetJavaScriptEnabled")
public class WebViewFragment extends OSGiFragment implements OnKeyListener
{
    private WebView webview;
    private ProgressBar progressbar;
    private HashMap<String, List<String>> notifs = new HashMap<>();
    private static Context instance;
    private static Stack<String> urlStack = new Stack<>();

    // stop webView.goBack() once we have started reload from urlStack
    private boolean isLoadFromStack = false;

    private String webUrl = null;
    private ValueCallback<Uri> mUploadMessage;
    private ValueCallback<Uri[]> mUploadMessageArray;

    private final static int FILE_REQUEST_CODE = 1;

    @SuppressLint("JavascriptInterface")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        // init webUrl with urlStack.pop() if non-empty, else load from default in DB
        if (urlStack.isEmpty()) {
            webUrl = ConfigurationUtils.getWebPage();
            urlStack.push(webUrl);
        }
        else {
            webUrl = urlStack.pop();
        }

        instance = getContext();
        View contentView = inflater.inflate(R.layout.webview_main, container, false);
        progressbar = contentView.findViewById(R.id.progress);
        progressbar.setIndeterminate(true);

        webview = contentView.findViewById(R.id.webview);

        final WebSettings webSettings = webview.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);

        webview.addJavascriptInterface(aTalkApp.getGlobalContext(), "Android");
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        if (Build.VERSION.SDK_INT >= 21) {
            webSettings.setMixedContentMode(0);
            webSettings.setAllowUniversalAccessFromFileURLs(true);
        }

        webview.setWebChromeClient(new WebChromeClient()
        {
            public void onProgressChanged(WebView view, int progress)
            {
                progressbar.setProgress(progress);
                if (progress < 100 && progress > 0 && progressbar.getVisibility() == ProgressBar.GONE) {
                    progressbar.setIndeterminate(true);
                    progressbar.setVisibility(ProgressBar.VISIBLE);
                }
                if (progress == 100) {
                    progressbar.setVisibility(ProgressBar.GONE);
                }
            }

            public void openFileChooser(ValueCallback<Uri> uploadMsg)
            {
                openFileChooser(uploadMsg, null, null);
            }

            public void openFileChooser(ValueCallback uploadMsg, String acceptType)
            {
                openFileChooser(uploadMsg, acceptType, null);
            }

            public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture)
            {
                mUploadMessage = uploadMsg;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(acceptType);

                Intent chooseFile = Intent.createChooser(i, "File Browser");
                startActivityForResult(chooseFile, FILE_REQUEST_CODE);
            }

            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> uploadMessageArray,
                    FileChooserParams fileChooserParams)
            {
                if (mUploadMessageArray != null)
                    mUploadMessageArray.onReceiveValue(null);

                mUploadMessageArray = uploadMessageArray;

                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");

                Intent chooseFile = Intent.createChooser(i, "File Browser");
                startActivityForResult(chooseFile, FILE_REQUEST_CODE);
                return true;
            }
        });

        webview.setWebViewClient(new MyWebViewClient(this));
        webview.loadUrl(webUrl);
        return contentView;
    }

    @Override
    public void onResume()
    {
        super.onResume();

        // setup keyPress listener - must re-enable every time on resume
        webview.setFocusableInTouchMode(true);
        webview.requestFocus();
        webview.setOnKeyListener(this);
    }

    // Prevent the webView from reloading on device rotation
    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
    }

    public static Context getInstance()
    {
        return instance;
    }

    /**
     * Init webView so it download root url stored in DB on next init
     */
    public static void initWebView()
    {
        urlStack.clear();
    }

    /**
     * Add the own loaded url page to stack for later retrieval (goBack)
     *
     * @param url loaded url
     */
    public void addUrl(String url)
    {
        urlStack.push(url);
        isLoadFromStack = false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != FILE_REQUEST_CODE || resultCode != Activity.RESULT_OK || data.getData() == null)
            return;

        if (Build.VERSION.SDK_INT >= 21) {
            if (mUploadMessageArray == null)
                return;

            mUploadMessageArray.onReceiveValue(new Uri[]{data.getData()});
            mUploadMessageArray = null;
        }
        else {
            if (mUploadMessage == null)
                return;

            mUploadMessage.onReceiveValue(data.getData());
            mUploadMessage = null;
        }
    }

    public static Bitmap getBitmapFromURL(String src)
    {
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

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event)
    {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            // android os will not pass in KEYCODE_MENU???
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                webview.loadUrl("javascript:MovimTpl.toggleMenu()");
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (!isLoadFromStack && webview.canGoBack()) {
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