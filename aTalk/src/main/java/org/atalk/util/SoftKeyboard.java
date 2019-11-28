package org.atalk.util;

/*
 * Author: Felipe Herranz (felhr85@gmail.com)
 * Contributors:Francesco Verheye (verheye.francesco@gmail.com)
 * 		Israel Dominguez (dominguez.israel@gmail.com)
 */

import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import timber.log.Timber;

public class SoftKeyboard implements View.OnFocusChangeListener
{
	private static final int CLEAR_FOCUS = 0;

	private ViewGroup layout;
	private int layoutBottom;
	private InputMethodManager imm;
	private int[] coords;
	private boolean isKeyboardShow;
	private SoftKeyboardChangesThread softKeyboardThread;
	private List<EditText> editTextList;

    // reference to a focused EditText
    private View tempView;

	public SoftKeyboard(ViewGroup layout, InputMethodManager imm)
	{
		this.layout = layout;
        keyboardHideByDefault();
		initEditTexts(layout);
		this.imm = imm;
		this.coords = new int[2];
		this.isKeyboardShow = false;
		this.softKeyboardThread = new SoftKeyboardChangesThread();
		this.softKeyboardThread.start();
	}

	public void openSoftKeyboard()
	{
		if (!isKeyboardShow) {
			layoutBottom = getLayoutCoordinates();
			imm.toggleSoftInput(0, InputMethodManager.SHOW_IMPLICIT);
			softKeyboardThread.keyboardOpened();
			isKeyboardShow = true;
		}
	}

	public void closeSoftKeyboard()
	{
		if (isKeyboardShow) {
			imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
			isKeyboardShow = false;
		}
	}

	public void setSoftKeyboardCallback(SoftKeyboardChanged mCallback)
	{
		softKeyboardThread.setCallback(mCallback);
	}

	public void unRegisterSoftKeyboardCallback()
	{
		softKeyboardThread.stopThread();
	}

	public interface SoftKeyboardChanged
	{
		void onSoftKeyboardHide();
		void onSoftKeyboardShow();
	}

	private int getLayoutCoordinates()
	{
		layout.getLocationOnScreen(coords);
		return coords[1] + layout.getHeight();
	}

	private void keyboardHideByDefault()
	{
		layout.setFocusable(true);
		layout.setFocusableInTouchMode(true);
	}

	/*
	 * InitEditTexts now handles EditTexts in nested views
	 * Thanks to Francesco Verheye (verheye.francesco@gmail.com)
	 */
	private void initEditTexts(ViewGroup viewgroup)
	{
		if (editTextList == null)
			editTextList = new ArrayList<>();

		int childCount = viewgroup.getChildCount();
		for (int i = 0; i <= childCount - 1; i++) {
			View v = viewgroup.getChildAt(i);

			if (v instanceof ViewGroup) {
				initEditTexts((ViewGroup) v);
			}

			if (v instanceof EditText) {
				EditText editText = (EditText) v;
				editText.setOnFocusChangeListener(this);
				editText.setCursorVisible(true);
				editTextList.add(editText);
			}
		}
	}

	/*
	 * OnFocusChange does update tempView correctly now when keyboard is still shown
	 * Thanks to Israel Dominguez (dominguez.israel@gmail.com)
	 */
	@Override
	public void onFocusChange(View v, boolean hasFocus)
	{
		if (hasFocus) {
			tempView = v;
			if (!isKeyboardShow) {
				layoutBottom = getLayoutCoordinates();
				softKeyboardThread.keyboardOpened();
				isKeyboardShow = true;
			}
		}
	}

	// This handler will clear focus of selected EditText
	private final Handler mHandler = new Handler()
	{
		@Override
		public void handleMessage(Message m)
		{
			switch (m.what) {
				case CLEAR_FOCUS:
					if (tempView != null) {
						tempView.clearFocus();
						tempView = null;
					}
					break;
			}
		}
	};

	private class SoftKeyboardChangesThread extends Thread
	{
		private AtomicBoolean started;
		private SoftKeyboardChanged mCallback;

		public SoftKeyboardChangesThread()
		{
			started = new AtomicBoolean(true);
		}

		public void setCallback(SoftKeyboardChanged mCallback)
		{
			this.mCallback = mCallback;
		}

		@Override
		public void run()
		{
			while (started.get()) {
				// Wait until keyboard is requested to open
				synchronized (this) {
					try {
						wait();
					}
					catch (InterruptedException e) {
                        Timber.w("Exception in starting keyboard thread: %s", e.getMessage());
					}
				}

				int currentBottomLocation = getLayoutCoordinates();
                // Timber.d("SoftKeyboard Current Bottom Location #1: %s (%s)", currentBottomLocation, layoutBottom);

				// There is some lag between open soft-keyboard function and when it really appears.
				while (currentBottomLocation == layoutBottom && started.get()) {
					currentBottomLocation = getLayoutCoordinates();
				}
                // Timber.d("SoftKeyboard Current Bottom Location #2: %s (%s)", currentBottomLocation, layoutBottom);

				if (started.get())
					mCallback.onSoftKeyboardShow();

				// When keyboard is opened from EditText, initial bottom location is greater than
				// layoutBottom and at some moment later <= layoutBottom.
				// That broke the previous logic, so I added this new loop to handle this.
				while (currentBottomLocation >= layoutBottom && started.get()) {
					currentBottomLocation = getLayoutCoordinates();
				}

				// Now Keyboard is shown, keep checking layout dimensions until keyboard is gone
				while (currentBottomLocation != layoutBottom && started.get()) {
                    // Timber.d("SoftKeyboard Current Bottom Location #3x %s (%s)", currentBottomLocation, layoutBottom);
					synchronized (this) {
						try {
							wait(500);
						}
						catch (InterruptedException e) {
							Timber.w("Exception in waiting for keyboard hide: %s", e.getMessage());
						}
					}
					currentBottomLocation = getLayoutCoordinates();
				}

				if (started.get())
					mCallback.onSoftKeyboardHide();

				// if keyboard has been opened clicking and EditText.
				if (isKeyboardShow && started.get())
					isKeyboardShow = false;

				// if an EditText is focused, remove its focus (on UI thread)
				if (started.get())
					mHandler.obtainMessage(CLEAR_FOCUS).sendToTarget();
			}
		}

		public void keyboardOpened()
		{
			synchronized (this) {
				notify();
			}
		}

		public void stopThread()
		{
			synchronized (this) {
				started.set(false);
				notify();
			}
		}
	}
}