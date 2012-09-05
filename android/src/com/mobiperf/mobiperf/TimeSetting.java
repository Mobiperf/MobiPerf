/* Copyright 2012 University of Michigan.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mobiperf.mobiperf;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Custom seekbar used for periodic testing.
 */
public class TimeSetting extends DialogPreference implements SeekBar.OnSeekBarChangeListener {

  private static final String androidns = "http://schemas.android.com/apk/res/android";

  private SeekBar mSeekBar;
  private TextView mSplashText, mValueText;
  private Context mContext;

  private String mDialogMessage, mSuffix;
  private String mMinutes, mHours;
  private int mDefault, mMax;

  private static int mValue = 0;

  public TimeSetting(Context context, AttributeSet attrs) {
    super(context, attrs);
    mContext = context;

    mDialogMessage = attrs.getAttributeValue(androidns, "dialogMessage");
    mSuffix = attrs.getAttributeValue(androidns, "text");
    mMinutes = " Minutes";
    mHours = " Hours";
    mDefault = attrs.getAttributeIntValue(androidns, "defaultValue", 0);
    mMax = attrs.getAttributeIntValue(androidns, "max", 100);
  }

  @Override
  protected View onCreateDialogView() {
    LinearLayout.LayoutParams params;
    LinearLayout layout = new LinearLayout(mContext);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(6, 6, 6, 6);

    mSplashText = new TextView(mContext);
    if (mDialogMessage != null)
      mSplashText.setText(mDialogMessage);
    layout.addView(mSplashText);

    mValueText = new TextView(mContext);
    mValueText.setGravity(Gravity.CENTER_HORIZONTAL);
    mValueText.setTextSize(32);
    params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    layout.addView(mValueText, params);

    mSeekBar = new SeekBar(mContext);
    mSeekBar.setOnSeekBarChangeListener(this);
    layout.addView(mSeekBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT));

    if (shouldPersist())
      mValue = getPersistedInt(mDefault);

    mSeekBar.setMax(mMax);
    mSeekBar.setProgress(mValue);
    return layout;
  }

  @Override
  protected void onBindDialogView(View v) {
    super.onBindDialogView(v);
    mSeekBar.setMax(mMax);
    mSeekBar.setProgress(mValue);
  }

  @Override
  protected void onSetInitialValue(boolean restore, Object defaultValue) {
    super.onSetInitialValue(restore, defaultValue);
    if (restore)
      mValue = shouldPersist() ? getPersistedInt(mDefault) : 0;
    else
      mValue = (Integer) defaultValue;
  }

  public void onProgressChanged(SeekBar seek, int value, boolean fromTouch) {
    // check for less than 1 hour here
    int tempval = value + 10;
    if (tempval < 60)
      mSuffix = mMinutes;
    else {
      tempval = value - 49;
      mSuffix = mHours;
    }
    String t = String.valueOf(tempval);
    mValueText.setText(mSuffix == null ? t : t.concat(mSuffix));
    if (shouldPersist())
      persistInt(value);
    callChangeListener(new Integer(value));
  }

  public void onStartTrackingTouch(SeekBar seek) {
  }

  public void onStopTrackingTouch(SeekBar seek) {
  }

  public void setMax(int max) {
    mMax = max;
  }

  public int getMax() {
    return mMax;
  }

  public void setProgress(int progress) {
    mValue = progress;
    if (mSeekBar != null)
      mSeekBar.setProgress(progress);
  }

  public static int getProgress() {
    return mValue;
  }

}
