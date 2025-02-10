package org.atalk.android.gui.chat;

import android.content.Intent;
import android.os.Bundle;

import java.net.URI;
import java.net.URISyntaxException;

import org.atalk.android.BaseActivity;

import timber.log.Timber;

/**
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class aTalkProtocolReceiver extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Timber.i("aTalk protocol intent received %s", intent);

        String urlStr = intent.getDataString();
        if (urlStr != null) {
            try {
                URI url = new URI(urlStr);
                ChatSessionManager.notifyChatLinkClicked(url);
            } catch (URISyntaxException e) {
                Timber.e(e, "Error parsing clicked URL");
            }
        }
        else {
            Timber.w("No URL supplied in aTalk link");
        }
        finish();
    }
}
