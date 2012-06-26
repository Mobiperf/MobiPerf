/* Copyright 2012 Google Inc.
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

package com.mobiperf.speedometer;

import android.util.Log;

/**
 * Wrapper for logging operations which can be disabled by setting LOGGING_ENABLED.
 * 
 * @author mdw@google.com (Matt Welsh)
 * 
 */
public class Logger {
  private final static boolean LOGGING_ENABLED = true;
  private final static String TAG = "MobiPerf";

  public static void d(String msg) {
    if (LOGGING_ENABLED) {
      Log.d(TAG, msg);
    }
  }

  public static void d(String msg, Throwable t) {
    if (LOGGING_ENABLED) {
      Log.d(TAG, msg, t);
    }
  }

  public static void e(String msg) {
    if (LOGGING_ENABLED) {
      Log.e(TAG, msg);
    }
  }

  public static void e(String msg, Throwable t) {
    if (LOGGING_ENABLED) {
      Log.e(TAG, msg, t);
    }
  }

  public static void i(String msg) {
    if (LOGGING_ENABLED) {
      Log.i(TAG, msg);
    }
  }

  public static void i(String msg, Throwable t) {
    if (LOGGING_ENABLED) {
      Log.i(TAG, msg, t);
    }
  }

  public static void v(String msg) {
    if (LOGGING_ENABLED) {
      Log.v(TAG, msg);
    }
  }

  public static void v(String msg, Throwable t) {
    if (LOGGING_ENABLED) {
      Log.v(TAG, msg, t);
    }
  }

  public static void w(String msg) {
    if (LOGGING_ENABLED) {
      Log.w(TAG, msg);
    }
  }

  public static void w(String msg, Throwable t) {
    if (LOGGING_ENABLED) {
      Log.w(TAG, msg, t);
    }
  }

}
