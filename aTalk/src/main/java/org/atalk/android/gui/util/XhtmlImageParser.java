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
package org.atalk.android.gui.util;

import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 * Utility class that implements <tt>Html.ImageGetter</tt> interface and can be used
 * to display url images in <tt>TextView</tt> through the HTML syntax.
 *
 * @author Eng Chong Meng
 */
public class XhtmlImageParser implements Html.ImageGetter
{
    private TextView mTextView;
    private String XhtmlString;

    /**
     * Construct the XhtmlImageParser which will execute AsyncTask and refresh the TextView
     * Usage: htmlTextView.setText(Html.fromHtml(HtmlString, new XhtmlImageParser(htmlTextView, HtmlString), null));
     *
     * @param tv the textView to be populated with return result
     * @param str the xhtml string
     */
    public XhtmlImageParser(TextView tv, String str)
    {
        mTextView = tv;
        XhtmlString = str;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Drawable getDrawable(String source)
    {
        HttpGetDrawableTask httpGetDrawableTask = new HttpGetDrawableTask();
        httpGetDrawableTask.execute(source);
        return null;
    }

    /**
     * Execute fetch url image as async task: else 'android.os.NetworkOnMainThreadException'
     */
    public class HttpGetDrawableTask extends AsyncTask<String, Void, Drawable>
    {
        @Override
        protected Drawable doInBackground(String... params)
        {
            String source = params[0];
            return getDrawable(source);
        }

        @Override
        protected void onPostExecute(Drawable result)
        {
            final Drawable urlDrawable = result;
            if (urlDrawable != null) {
                mTextView.setText(Html.fromHtml(XhtmlString, source -> urlDrawable, null));
            }
            else {
                mTextView.setText(Html.fromHtml(XhtmlString, null, null));
            }
            mTextView.setMovementMethod(LinkMovementMethod.getInstance());
        }

        /***
         * Get the Drawable from the given URL (change to secure https if necessary)
         * aTalk/android supports only secure https connection
         *
         * @param urlString url string
         * @return drawable
         */
        public Drawable getDrawable(String urlString)
        {
            try {
                // urlString = "https://cmeng-git.github.io/atalk/img/09.atalk_avatar.png";
                urlString = urlString.replace("http:", "https:");

                URL sourceURL = new URL(urlString);
                URLConnection urlConnection = sourceURL.openConnection();
                urlConnection.connect();
                InputStream inputStream = urlConnection.getInputStream();

                Drawable drawable = Drawable.createFromStream(inputStream, "src");
                if (drawable != null)
                    drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());

                return drawable;
            } catch (IOException e) {
                return null;
            }
        }
    }
}
