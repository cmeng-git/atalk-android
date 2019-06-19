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

package org.atalk.android.gui;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import org.atalk.android.R;

/**
 * Splash screen activity
 */
public class Splash extends Activity
{
    private static boolean mFirstRun = true;

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        // Request indeterminate progress for splash screen
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        // requestWindowFeature(Window.FEATURE_NO_TITLE);

        setProgressBarIndeterminateVisibility(true);
        setContentView(R.layout.splash);

        // Starts fade in animation
        ImageView myImageView = findViewById(R.id.loadingImage);
        Animation myFadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        myImageView.startAnimation(myFadeInAnimation);
        mFirstRun = false;

        new Handler().postDelayed(() -> {
            finish();
        }, 800);
    }

    static public boolean isFirstRun()
    {
        return mFirstRun;
    }
}
