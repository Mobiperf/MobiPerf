package com.google.wireless.speed.speedometer;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.ExecutionException;

import org.apache.http.cookie.Cookie;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * The main activity of the Speedometer application.
 * 
 * @author mdw@google.com (Matt Welsh)
 */
public class Speedometer extends Activity implements LocationListener {
  public static final String TAG = "Speedometer";
  public static final int REQUEST_LOGIN = 1;

  private Button runButton;
  private Button loginButton;
  private Spinner measurementTypeSpinner;
  private String measurementType;
  private static LocationManager locMgr;
  private static TelephonyManager telephonyMgr;
  private Location curLocation = null;
  private TextView statusText, locationText;
  
  private static String deviceID;
  private Checkin checkin;
  private Cookie loginCookie;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);
    
    runButton = (Button) findViewById(R.id.runButton);
    runButton.setOnClickListener(new RunButtonListener());
    loginButton = (Button) findViewById(R.id.loginButton);
    loginButton.setOnClickListener(new LoginButtonListener());
    
    measurementTypeSpinner = 
      (Spinner)findViewById(R.id.measurementTypeSpinner);
    ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
            this, R.array.measurementTypes,
            android.R.layout.simple_spinner_item);
    adapter.setDropDownViewResource(
        android.R.layout.simple_spinner_dropdown_item);
    measurementTypeSpinner.setAdapter(adapter);
    measurementTypeSpinner.setOnItemSelectedListener(
        new MeasurementTypeSelectedListener());
    
    locMgr = (LocationManager) getSystemService(LOCATION_SERVICE);
    locMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
    deviceID = Secure.getString(getBaseContext().getContentResolver(),
        Secure.ANDROID_ID);
    
    telephonyMgr = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
    
    statusText = (TextView) findViewById(R.id.StatusText);
    locationText = (TextView) findViewById(R.id.LocationText);
    
    checkin = new Checkin(this);
  }
  
  @Override
  protected void onResume() {
    super.onResume();
    PhoneUtils.setGlobalContext(getApplication());
  }
  
  @Override
  protected void onPause() {
    PhoneUtils.releaseGlobalContext();
    super.onPause();
  }

  
  public class LoginButtonListener implements OnClickListener {
    @Override
    public void onClick(View view) {
      Log.i(TAG, "LoginButton clicked");
      checkin.getCookie();
    }
  }
    
  
  protected void onActivityResult(int requestCode, int resultCode,
      Intent data) {
    Log.i(TAG, "onActivityResult returned req " + requestCode +
        " result " + resultCode + " data " + data);
  }
  
  public class RunButtonListener implements OnClickListener {
    @Override
    public void onClick(View view) {
      doMeasurement();
    }
  }
  
  public class MeasurementTypeSelectedListener
    implements OnItemSelectedListener {
    public void onItemSelected(AdapterView<?> parent,
        View view, int pos, long id) {
      measurementType = parent.getItemAtPosition(pos).toString();
    }

    public void onNothingSelected(AdapterView parent) {
      // Do nothing.
    }
  }
  
  private void doMeasurement() {
    Log.i(TAG, "doMeasurement called, measurement type " + measurementType);
    statusText.setText("Measurement type: " + measurementType);
    
    MeasurementTask task = MeasurementTask.makeMeasurementTask(
        measurementType, null, null, null, null, null, null, new HashMap<String, String>());
    if (task == null) {
      Log.w(TAG, "Unsupported measurement type " + measurementType);
      statusText.setText("Unsupported measurement type " + measurementType);
      return;
    }
    
    Vector<MeasurementTask> toadd = new Vector<MeasurementTask>();
    toadd.add(task);
    Log.i(TAG, "Scheduling measurement...");
    checkin.getScheduler().addToSchedule(toadd);
  }

  @Override
  public void onLocationChanged(Location location) {
    Log.i(TAG, "onLocationChanged: " + location);
    TextView locationText = (TextView) findViewById(R.id.LocationText);
    locationText.setText("Got location from onLocationChanged: " + location);
    curLocation = location;
  }

  @Override
  public void onProviderDisabled(String provider) {
    // Empty
  }

  @Override
  public void onProviderEnabled(String provider) {
    // Empty
  }

  @Override
  public void onStatusChanged(String provider, int status, Bundle extras) {
    // Empty
  }
  
  public void setStatus(String status) {
    Log.i(TAG, status);
    statusText.setText(status);
  }

  public static String getDeviceId() {
    return deviceID;
  }
  
  public static JSONObject getDeviceProperties() {
    JSONObject properties = new JSONObject();
    try {
      properties.put("os_version", Build.VERSION.RELEASE);
      properties.put("carrier", telephonyMgr.getNetworkOperatorName());
      properties.put("network_type", PhoneUtils.getPhoneUtils().getNetwork());
    } catch (JSONException e) {
      Log.e(TAG, "Unable to set device properties: " + e);
    }
    return properties;
  }
  
  public Location getCurLocation() {
    return curLocation;
  }
  

}
