/*
 * Copyright 2011 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.wireless.speed.speedometer;

import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Runs a process and captures its output and exit code.
 * <p>
 * Use {@link PhoneUtils#startProcess} to create a ProcessRunner.
 * <p>
 * The code is loosely based on class com.google.testing.util.ProcessRunner,
 * with some interface modifications (e.g. stdout/err streams, async launch)
 * and Android adaptations. That class is deprecated in favor of
 * com.google.io.base.shell.Command,
 * but Command is way too complicated and has too many dependencies
 * to port it all to Android. For now we only need something very simple,
 * no need for all the power of Command.
 * <p>
 * Example:
 * <pre>
 *    ProcessRunner runner = PhoneUtils.getPhoneUtils().startProcess(
 *        Arrays.asList("echo", "foo"), System.out, System.err);
 *    if (runner.waitFor() != 0) {
 *      System.out.println("Error, exit code: " +  runner.getExitValue());
 *    }
 * </pre>
 * The command runs in the app home directory.
 *
 * @author klm@google.com (Michael Klepikov) - Android adaptation
 * @author treitel@google.com (Richard Treitel) - original ProcessRunner
 * @author mdevin@google.com (Matthieu Devin) - original ProcessRunner
 */
public class ProcessRunner {

  private static final String DEBUG_TAG = ProcessRunner.class.getSimpleName();

  /** Initial byte capacity for buffer where we collect process output. */
  private static final int INITIAL_OUT_CAPACITY = 16 * 1024;

  /** Max size of a single read from the process output or errThread stream. */
  private static final int READ_BUFFER_SIZE = 8192;

  /** The process command line as a list of strings. */
  private final List<String> listCommand;

  /** Log tag specific to the command. */
  private final String cmdDebugTag;

  // The launched process
  private Process process;

  // The process results
  private int exitValue = -1;

  // The latch implements waiting for stdout and stderr capture to finish
  private CountDownLatch captureCompleted;

  /** Constructs a new process runner for a given command. */
  protected ProcessRunner(List<String> command) {
    assert command != null;
    assert command.size() > 0;

    listCommand = command;
    cmdDebugTag = DEBUG_TAG + "." + command.get(0);
  }

  /**
   * Starts the process.
   *
   * @param outStream  where to capture process stdout
   * @param errStream  where to capture process stderr
   * @throws IOException  if an error occurs while starting
   */
  protected void start(OutputStream outStream, OutputStream errStream)
      throws IOException {
    // Fork the process.
    //ArrayList<String> sudoListCommand = new ArrayList<String>();
    //sudoListCommand.add("sudo");
    //sudoListCommand.addAll(listCommand);
    //listCommand = sudoListCommand;
    Log.i(cmdDebugTag, "Starting: " + listCommand);
    ProcessBuilder builder = new ProcessBuilder(listCommand);
    builder.directory(new File(System.getProperty("user.dir")));
    exitValue = -1;

    process = builder.start();

    // Capture its stdout and stderr streams.
    captureCompleted = new CountDownLatch(2);  // Capture threads decrement it
    capture(process.getInputStream(), outStream, "stdout");
    capture(process.getErrorStream(), errStream, "stderr");
  }

  /**
   * Starts the process.
   *
   * @param outStream  where to capture process stdout
   * @param errStream  where to capture process stderr
   * @throws IOException  if an error occurs while starting
   */
  protected void start1(OutputStream outStream, OutputStream errStream)
      throws IOException {
    if (process != null) {
      process.destroy();
    }

    // Preform su to get root privledges
    process = Runtime.getRuntime().exec("su");
    DataOutputStream shell = new DataOutputStream(process.getOutputStream());
    shell.writeBytes("echo \"Do I have root?\" >/system/temporary.txt\n");
    // Run the target command in the su shell.
    shell.writeBytes(getCommandString() + " && exit\n");
    shell.flush();

    // Capture its stdout and stderr streams.
    captureCompleted = new CountDownLatch(2);  // Capture threads decrement it
    capture(process.getInputStream(), outStream, "stdout");
    capture(process.getErrorStream(), errStream, "stderr");
  }


  protected String getCommandString() {
    String cmd = "";
    for (String s : listCommand) {
      cmd += "\"" + s.replace("\"", "\\\"") + "\" ";
    }

    return cmd;
  }

  /**
   * Waits for the process to terminate and returns the exit code.
   *
   * @return the process exit value. 0 usually means success.
   * @throws InterruptedException  if the wait is interrupted
   */
  public int waitFor() throws InterruptedException {
    // Wait for everything to terminate.
    Log.i(cmdDebugTag, "Waiting for the process to finish");
    exitValue = process.waitFor();
    Log.d(cmdDebugTag, "Process finished with status " + exitValue);
    captureCompleted.await();
    return exitValue;
  }

  /** Returns the exit code of the process. */
  public int getExitValue() {
    if (exitValue == -1) {
      throw new IllegalStateException(
          "Must call waitFor() before calling getExitValue()");
    }
    return exitValue;
  }

  /**
   * Interprets a byte array as UTF8-encoded stream and reads text lines.
   *
   * @param bytes  the byte array to read from
   * @param lines  the list of strings to read into
   * @throws IOException  if a UTF8 decoding error occurs
   */
  private static void readLinesFromBytes(byte[] bytes, List<String> lines)
      throws IOException {
    BufferedReader br = new BufferedReader(
        new InputStreamReader(new ByteArrayInputStream(bytes), "UTF-8"),
        READ_BUFFER_SIZE);
    for (String line = null; (line = br.readLine()) != null; ) {
      lines.add(line);
    }
  }

  /**
   * Runs a command and returns combined list of stdout and stderr lines.
   *
   * Assumes UTF-8 encoding of the output.
   *
   * @param requireZeroExit  if true, throw IOException on nonzero exit code.
   * @return a list of stdout + stderr lines: always stdout first, stderr after.
   * @throws IOException if there is an error running the command, or if
   *     requireZeroExit was true, but the command returned a nonzero exit code.
   */
  protected List<String> runCommandGetOutput(boolean requireZeroExit)
      throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream(INITIAL_OUT_CAPACITY);
    ByteArrayOutputStream err = new ByteArrayOutputStream(INITIAL_OUT_CAPACITY);
    start(out, err);
    try {
      int exitCode = waitFor();
      if (requireZeroExit && exitCode != 0) {
        throw new IOException("command exited with status " + exitCode);
      }
    } catch (InterruptedException e) {
      Log.e(DEBUG_TAG, "Process wait interrupted", e);
      Thread.currentThread().interrupt();
      throw new IOException("interrupted waiting for the command to finish");
    }

    // Take the process output and interpret it as UTF-8 encoded lines of text.
    byte[] outBytes = out.toByteArray();
    byte[] errBytes = err.toByteArray();

    List<String> outputLines = new ArrayList<String>();
    readLinesFromBytes(outBytes, outputLines);
    readLinesFromBytes(errBytes, outputLines);
    return outputLines;
  }

  /** Creates and starts a thread to copy an InputStream into OutputStream. */
  private Thread capture(final InputStream in,
                         final OutputStream out,
                         final String name) {
    Thread thread = new Thread() {
        @Override public void run() {
          String captureDebugTag = cmdDebugTag + "." + name;
          byte[] readBuffer = new byte[READ_BUFFER_SIZE];

          try {
            long totalReadSize = 0;
            Log.d(captureDebugTag, "Starting to intercept process output");
            // TODO(klm): Move IOUtils from gshots to com.google.wireless.speed.velodrome
            // and use copy(InputStream, OutputStream)
            int readSize;
            while (0 < (readSize = in.read(readBuffer))) {
              out.write(readBuffer, 0, readSize);
              totalReadSize += readSize;
            }
            Log.d(captureDebugTag, "End of stream reached, " + totalReadSize +
                  " bytes read");
          } catch (IOException x) {
            Log.e(captureDebugTag, x.toString(), x);
          } finally {
            captureCompleted.countDown();
            try { in.close(); } catch (IOException unused) { /* Ignored */ }
          }
        }
      };
    thread.start();
    return thread;
  }
}
