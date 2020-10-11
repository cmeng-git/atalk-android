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

import android.os.Bundle;
import android.os.Handler;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ProgressBar;

import org.atalk.android.R;
import org.atalk.service.osgi.OSGiActivity;

import timber.log.Timber;

/**
 * Splash screen for aTalk start up
 *
 * @author Eng Chong Meng
 */
public class Splash extends OSGiActivity
{
    private static boolean mFirstRun = true;
    private ProgressBar mActionBarProgress;

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.splash);
        mActionBarProgress = findViewById(R.id.actionbar_progress);
        mActionBarProgress.setVisibility(ProgressBar.VISIBLE);

        // Starts fade in animation
        ImageView myImageView = findViewById(R.id.loadingImage);
        Animation myFadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        myImageView.startAnimation(myFadeInAnimation);
        mFirstRun = false;

        new Handler().postDelayed(() -> {
            Timber.d("End of Splash screen Timer");
            finish();
        }, 800);
    }

    public static boolean isFirstRun()
    {
        return mFirstRun;
    }
}
