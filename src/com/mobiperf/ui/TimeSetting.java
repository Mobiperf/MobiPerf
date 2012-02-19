/****************************
 * This file is part of the MobiPerf project (http://mobiperf.com). 
 * We make it open source to help the research community share our efforts.
 * If you want to use all or part of this project, please give us credit and cite MobiPerf's official website (mobiperf.com).
 * The package is distributed under license GPLv3.
 * If you have any feedbacks or suggestions, don't hesitate to send us emails (3gtest@umich.edu).
 * The server suite source code is not included in this package, if you have specific questions related with servers, please also send us emails
 * 
 * Contact: 3gtest@umich.edu
 * Development Team: Junxian Huang, Birjodh Tiwana, Zhaoguang Wang, Zhiyun Qian, Cheng Chen, Yutong Pei, Feng Qian, Qiang Xu
 * Copyright: RobustNet Research Group led by Professor Z. Morley Mao, (Department of EECS, University of Michigan, Ann Arbor) and Microsoft Research
 *
 ****************************/

package com.mobiperf.ui;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;


public class TimeSetting extends DialogPreference implements SeekBar.OnSeekBarChangeListener{

	  private static final String androidns="http://schemas.android.com/apk/res/android";

	  private SeekBar mSeekBar;
	  private TextView mSplashText,mValueText;
	  private Context mContext;

	  private String mDialogMessage, mSuffix;
	  private String mMinutes, mHours;
	  private int mDefault, mMax;

	private static int mValue = 0;

	  public TimeSetting(Context context, AttributeSet attrs) { 
	    super(context,attrs); 
	    mContext = context;

	    mDialogMessage = attrs.getAttributeValue(androidns,"dialogMessage");
	    mSuffix = attrs.getAttributeValue(androidns,"text");
	    mMinutes = " Minutes";
	    mHours = " Hours";
	    mDefault = attrs.getAttributeIntValue(androidns,"defaultValue", 0);
	    mMax = attrs.getAttributeIntValue(androidns,"max", 100);
	  }
	  @Override 
	  protected View onCreateDialogView() {
	    LinearLayout.LayoutParams params;
	    LinearLayout layout = new LinearLayout(mContext);
	    layout.setOrientation(LinearLayout.VERTICAL);
	    layout.setPadding(6,6,6,6);

	    mSplashText = new TextView(mContext);
	    if (mDialogMessage != null)
	      mSplashText.setText(mDialogMessage);
	    layout.addView(mSplashText);

	    mValueText = new TextView(mContext);
	    mValueText.setGravity(Gravity.CENTER_HORIZONTAL);
	    mValueText.setTextSize(32);
	    params = new LinearLayout.LayoutParams(
	        LinearLayout.LayoutParams.FILL_PARENT, 
	        LinearLayout.LayoutParams.WRAP_CONTENT);
	    layout.addView(mValueText, params);

	    mSeekBar = new SeekBar(mContext);
	    mSeekBar.setOnSeekBarChangeListener(this);
	    layout.addView(mSeekBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

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
	  protected void onSetInitialValue(boolean restore, Object defaultValue)  
	  {
	    super.onSetInitialValue(restore, defaultValue);
	    if (restore) 
	      mValue = shouldPersist() ? getPersistedInt(mDefault) : 0;
	    else 
	      mValue = (Integer)defaultValue;
	  }

	  public void onProgressChanged(SeekBar seek, int value, boolean fromTouch)
	  {
	    //check for less than 1 hour here
	    int tempval = value+10;
		if (tempval < 60) mSuffix = mMinutes;
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
	  public void onStartTrackingTouch(SeekBar seek) {}
	  public void onStopTrackingTouch(SeekBar seek) {}

	  public void setMax(int max) { mMax = max; }
	  public int getMax() { return mMax; }

	  public void setProgress(int progress) { 
	    mValue = progress;
	    if (mSeekBar != null)
	      mSeekBar.setProgress(progress); 
	  }
	  public static int getProgress() { return mValue; }

}
