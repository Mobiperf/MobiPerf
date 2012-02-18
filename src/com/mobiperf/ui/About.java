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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

public class About extends Activity {
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.about);
	    TextView textView = (TextView)findViewById(R.id.about2);
	    textView.setMovementMethod(ScrollingMovementMethod.getInstance());
	}
	
	/******************** Menu starts here by cc ********************/
    // Define menu ids
	protected static final int NEW_TEST = Menu.FIRST;
	//TODO:new menu --------cc
	protected static final int PAST_RECORD = Menu.FIRST +5;
	// Create menu
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	        super.onCreateOptionsMenu(menu);

	        menu.add(0, NEW_TEST, 0, "New Test");
	      //TODO:new menu --------cc
	        menu.add(0, PAST_RECORD, 0, "View past record");
	        return true;
	}
	// Deal with menu event
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	        super.onOptionsItemSelected(item);
	        Log.v("menu","onOptionsItemSelected "+item.getItemId());
	        switch (item.getItemId()) {
	       /*
	        case NEW_TEST:
	        	Intent i = new Intent(this, com.mobiperf.lte.Main.class);
				 startActivityForResult(i, 0);
                break;
           */     
	        }
	        return true;
		
	}

/******************** Menu ends here ********************/

}
