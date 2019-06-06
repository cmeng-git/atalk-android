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
import android.app.AlertDialog.Builder;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.view.View.OnKeyListener;
import android.webkit.*;
import android.widget.*;

import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.android.R;
import org.atalk.service.osgi.OSGiFragment;
import org.atalk.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;

@SuppressLint("SetJavaScriptEnabled")
public class WebViewFragment extends OSGiFragment implements OnKeyListener
{
    private WebView webview;
    private ProgressBar progressbar;
    private HashMap<String, List<String>> notifs = new HashMap<>();
    private static Context instance;

    private String webUrl;
    private ValueCallback<Uri> mUploadMessage;
    private ValueCallback<Uri[]> mUploadMessageArray;
    private final static int FILE_REQUEST_CODE = 1;

    @SuppressLint("JavascriptInterface")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        instance = getContext();
        webUrl = ConfigurationUtils.getWebPage();
        View contentView = inflater.inflate(R.layout.webview_main, container, false);
        progressbar = contentView.findViewById(R.id.progress);
        progressbar.setIndeterminate(true);

        webview = contentView.findViewById(R.id.webview);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.getSettings().setDomStorageEnabled(true);

        if (Build.VERSION.SDK_INT >= 21) {
            webview.getSettings().setMixedContentMode(0);
            webview.getSettings().setAllowUniversalAccessFromFileURLs(true);
        }

        if (Build.VERSION.SDK_INT >= 17)
            webview.addJavascriptInterface(this, "Android");

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
                openFileChooser(uploadMsg, null, null);
            }

            public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture)
            {
                mUploadMessage = uploadMsg;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                WebViewFragment.this.startActivityForResult(Intent.createChooser(i, "File Browser"), FILE_REQUEST_CODE);
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

                Intent ci = new Intent(Intent.ACTION_CHOOSER);
                ci.putExtra(Intent.EXTRA_INTENT, i);
                ci.putExtra(Intent.EXTRA_TITLE, "File Browser");
                startActivityForResult(ci, FILE_REQUEST_CODE);
                return true;
            }
        });

        webview.setWebViewClient(new WebViewClient()
        {
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl)
            {
                webview.loadUrl("file:///android_asset/movim/error.html");
            }

            public boolean shouldOverrideUrlLoading(WebView view, String url)
            {
                String origin = Uri.parse(view.getUrl()).getHost();
                String aim = Uri.parse(url).getHost();

                if (StringUtils.isNullOrEmpty(origin) || origin.equals(aim)) {
                    return false;
                }

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(url));
                startActivity(intent);
                return true;
            }

            public void onReceivedSslError(WebView view, final SslErrorHandler handler, SslError error)
            {
                webview.loadUrl("file:///android_asset/movim/ssl.html");
            }

            public void onReceivedHttpAuthRequest(final WebView view, final HttpAuthHandler handler, final String host,
                    final String realm)
            {
                final String[] httpAuth = new String[2];
                final String[] viewAuth = view.getHttpAuthUsernamePassword(host, realm);
                final EditText usernameInput = new EditText(instance);
                final EditText passwordInput = new EditText(instance);

                httpAuth[0] = viewAuth != null ? viewAuth[0] : "";
                httpAuth[1] = viewAuth != null ? viewAuth[1] : "";

                usernameInput.setHint("Username");
                passwordInput.setHint("Password");
                passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

                LinearLayout ll = new LinearLayout(instance);
                ll.setOrientation(LinearLayout.VERTICAL);
                ll.addView(usernameInput);
                ll.addView(passwordInput);

                Builder authDialog = new Builder(instance).setTitle("Please login")
                        .setView(ll).setCancelable(false)
                        .setPositiveButton("OK", (dialog, whichButton) -> {
                            httpAuth[0] = usernameInput.getText().toString();
                            httpAuth[1] = passwordInput.getText().toString();
                            view.setHttpAuthUsernamePassword(host, realm, httpAuth[0], httpAuth[1]);
                            handler.proceed(httpAuth[0], httpAuth[1]);
                            dialog.dismiss();
                        });

                if (!handler.useHttpAuthUsernamePassword()) {
                    authDialog.show();
                }
                else {
                    handler.proceed(httpAuth[0], httpAuth[1]);
                }
            }
        });

        // setup keyPress listener
        contentView.setFocusableInTouchMode(true);
        contentView.requestFocus();
        contentView.setOnKeyListener(this);

        // webview.loadUrl("file:///android_asset/movim/index.html");
        return contentView;
    }

    @Override
    public void onResume()
    {
        super.onResume();
        webview.loadUrl(webUrl);
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
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                webview.loadUrl("javascript:MovimTpl.toggleMenu()");
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                // webview.loadUrl("javascript:MovimTpl.back()");
                webview.loadUrl(webUrl);
                return true;
            }
        }
        return false;
    }
}