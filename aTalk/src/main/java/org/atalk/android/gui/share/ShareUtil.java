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

package org.atalk.android.gui.share;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.text.Html;
import android.text.TextUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.persistance.FileBackend;

import java.util.ArrayList;

import androidx.annotation.RequiresApi;
import timber.log.Timber;

/**
 * @author Eng Chong Meng
 */
public class ShareUtil
{
    private static final int REQUEST_CODE_SHARE = 500;

    /**
     * Total wait time for handle multiple intents (text & images)
     */
    private static final int TIME_DELAY = 12000;

    /**
     * Share of both text and images with auto start of second intend with a timeDelay in between
     * OS >= VERSION_CODES.LOLLIPOP_MR1 uses pendingIntent call back (broadcast)
     *
     * @param activity a reference of the activity
     * @param msgContent text content for sharing
     * @param imageUris array of image uris for sharing
     */
    @SuppressLint("NewApi")
    public static void share(final Activity activity, String msgContent, ArrayList<Uri> imageUris)
    {
        if (activity != null) {
            int timeDelay = 0;

            if (!TextUtils.isEmpty(msgContent)) {
                Intent shareIntent = share(activity, msgContent);
                try {
                    if (!imageUris.isEmpty() && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1)) {
                        PendingIntent pi = PendingIntent.getBroadcast(activity, REQUEST_CODE_SHARE,
                                new Intent(activity, ShareBroadcastReceiver.class),
                                PendingIntent.FLAG_UPDATE_CURRENT);
                        activity.startActivity(Intent.createChooser(shareIntent,
                                activity.getString(R.string.service_gui_SHARE_TEXT), pi.getIntentSender()));

                        // setup up media file sending intent
                        ShareBroadcastReceiver.setShareIntent(activity, share(activity, imageUris));
                        return;
                    }
                    else {
                        // setting is used only when !imageUris.isEmpty()
                        timeDelay = TIME_DELAY;
                        activity.startActivity(Intent.createChooser(shareIntent,
                                activity.getString(R.string.service_gui_SHARE_TEXT)));
                    }
                } catch (ActivityNotFoundException e) {
                    Timber.w("%s", aTalkApp.getResString(R.string.no_application_found_to_open_file));
                }
            }

            if (!imageUris.isEmpty()) {
                // must wait for user first before starting file transfer if any
                new Handler().postDelayed(() -> {
                    Intent intent = share(activity, imageUris);
                    try {
                        activity.startActivity(Intent.createChooser(intent, activity.getText(R.string.service_gui_SHARE_FILE)));
                    } catch (ActivityNotFoundException e) {
                        Timber.w("No application found to open file");
                    }
                }, timeDelay);
            }
        }
    }

    /**
     * Generate a share intent with the given msgContent
     *
     * @param activity a reference of the activity
     * @param msgContent text content for sharing
     * @return share intent of the given msgContent
     */
    public static Intent share(Activity activity, String msgContent)
    {
        Intent shareIntent = null;
        if ((activity != null) && (!TextUtils.isEmpty(msgContent))) {
            shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);

            // replace all "\n" with <br/> to avoid strip by Html.fromHtml
            msgContent = msgContent.replaceAll("\n", "<br/>");
            msgContent = Html.fromHtml(msgContent).toString();
            msgContent = msgContent.replaceAll("<br/>", "\n");

            shareIntent.putExtra(Intent.EXTRA_TEXT, msgContent);
            shareIntent.setType("text/plain");
        }
        return shareIntent;
    }

    /**
     * Generate a share intent with the given imageUris
     *
     * @param context a reference context of the activity
     * @param imageUris array of image uris for sharing
     * @return share intent of the given imageUris
     */
    public static Intent share(Context context, ArrayList<Uri> imageUris)
    {
        Intent shareIntent = null;
        if ((context != null) && !imageUris.isEmpty()) {
            shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
            shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, imageUris);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            String mimeType = getMimeType(context, imageUris);
            shareIntent.setType(mimeType);
        }
        return shareIntent;
    }

    /**
     * Share of both text and images in a single intent for local forward only in aTalk
     * msgContent is saved intent.categories if both types are required; otherwise follow standard share method
     *
     * @param context a reference context of the activity
     * @param shareLocal a reference of the ShareActivity
     * @param msgContent text content for sharing
     * @param imageUris array of image uris for sharing
     */
    public static Intent shareLocal(Context context, Intent shareLocal, String msgContent, ArrayList<Uri> imageUris)
    {
        if ((context != null) && (shareLocal != null)) {

            if (!imageUris.isEmpty()) {
                shareLocal.setAction(Intent.ACTION_SEND_MULTIPLE);
                shareLocal.putParcelableArrayListExtra(Intent.EXTRA_STREAM, imageUris);
                shareLocal.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                String mimeType = getMimeType(context, imageUris);
                shareLocal.setType(mimeType);

                // Pass the extra_text in intent.categories in this case
                if (!TextUtils.isEmpty(msgContent)) {
                    shareLocal.addCategory(msgContent);
                }
            }
            else if (!TextUtils.isEmpty(msgContent)) {
                shareLocal.setAction(Intent.ACTION_SEND);
                shareLocal.putExtra(Intent.EXTRA_TEXT, msgContent);
                shareLocal.setType("text/plain");
            }
        }
        return shareLocal;
    }

    /**
     * Generate a common mime type for the given imageUris; reduce in resolution with more than one image types
     *
     * @param context a reference context of the activity
     * @param imageUris array of image uris for sharing
     * @return th common mime type for the given imageUris
     */
    private static String getMimeType(Context context, ArrayList<Uri> imageUris)
    {
        String tmp;
        String[] mimeTmp;
        String[] mimeType = {"*", "*"};

        int first = 0;
        for (Uri uri : imageUris) {
            tmp = FileBackend.getMimeType(context, uri);
            if (tmp != null) {
                mimeTmp = tmp.split("/");
                if (first++ == 0) {
                    mimeType = mimeTmp;
                }
                else {
                    if (!mimeType[0].equals(mimeTmp[0]))
                        mimeType[0] = "*";
                    if (!mimeType[1].equals(mimeTmp[1]))
                        mimeType[1] = "*";
                }
            }
        }
        return mimeType[0] + "/" + mimeType[1];
    }

    /**
     * Share BroadcastReceiver call back after user has choose the share app
     * Some delay is given for user to pick the buddy before starting the next share intent
     */
    public static class ShareBroadcastReceiver extends BroadcastReceiver
    {
        private static Intent mediaIntent;

        public static void setShareIntent(Activity activity, Intent intent)
        {
            mediaIntent = Intent.createChooser(intent, activity.getText(R.string.service_gui_SHARE_FILE));
            mediaIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
        @Override
        public void onReceive(Context context, Intent intent)
        {
            ComponentName clickedComponent = intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT);

            if (mediaIntent == null)
                return;

            // must wait for user to complete text share before starting file share
            new Handler().postDelayed(() -> {
                try {
                    context.startActivity(mediaIntent);
                    mediaIntent = null;
                } catch (ActivityNotFoundException e) {
                    Timber.w("No application found to open file");
                }
            }, TIME_DELAY / 2);
        }
    }
}
