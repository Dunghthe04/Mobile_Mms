package com.mkac.meikomms.common;

import android.util.Log;

public class ColorConsole
{
    /*
     * debug variable enables/disables all log messages to logcat
     * Useful to disable prior to app store submission
     */
    public static final boolean debug = true;
    public static final boolean info = true;
    public static final boolean warning = true;
    public static final boolean error = true;
    public static final boolean detail = true;
    public static void d(String s) {
        if (debug) {
            String[] msg = trace(Thread.currentThread().getStackTrace(), 3);
            Log.d(msg[0] + ":" + msg[1], msg[0] + ":" + msg[1] + " " + s);
        } else {
            return;
        }
    }
    public static void d(String t, String s) {
        if (debug) {
            String[] msg = trace(Thread.currentThread().getStackTrace(), 3);
            Log.d(t,  msg[0] + ":" + msg[1] + " " + s);
        } else {
            return;
        }
    }

    public static void i(String s) {
        if (info) {
            String[] msg = trace(Thread.currentThread().getStackTrace(), 3);
            Log.i(msg[0] + ":" + msg[1], msg[0] + ":" + msg[1] + " " + s);
        } else {
            return;
        }
    }
    public static void i(String t, String s) {
        if (info) {
            String[] msg = trace(Thread.currentThread().getStackTrace(), 3);
            Log.i(t,  msg[0] + ":" + msg[1] + " " + s);
        } else {
            return;
        }
    }
    public static void w(String s) {
        if (warning) {
            String[] msg = trace(Thread.currentThread().getStackTrace(), 3);
            Log.w(msg[0] + ":" + msg[1], msg[0] + ":" + msg[1] + " " + s);
        } else {
            return;
        }
    }
    public static void w(String t, String s) {
        if (warning) {
            String[] msg = trace(Thread.currentThread().getStackTrace(), 3);
            Log.w(t,  msg[0] + ":" + msg[1] + " " + s);
        } else {
            return;
        }
    }
    public static void e(String s) {
        if (error) {
            String[] msg = trace(Thread.currentThread().getStackTrace(), 3);
            Log.e(msg[0] + ":" + msg[1], msg[0] + ":" + msg[1] + " " + s);
        } else {
            return;
        }
    }
    public static void e(String t, String s) {
        if (error) {
            String[] msg = trace(Thread.currentThread().getStackTrace(), 3);
            Log.e(t,  msg[0] + ":" + msg[1] + " " + s);
        } else {
            return;
        }
    }

    public static void v(String s) {
        if (detail) {
            String[] msg = trace(Thread.currentThread().getStackTrace(), 3);
            Log.v(msg[0] + ":" + msg[1], msg[0] + ":" + msg[1] + " " + s);
        } else {
            return;
        }
    }
    public static void v(String t, String s) {
        if (detail) {
            String[] msg = trace(Thread.currentThread().getStackTrace(), 3);
            Log.v(t,  msg[0] + ":" + msg[1] + " " + s);
        } else {
            return;
        }
    }
    /*
     * trace
     * Gathers the calling file, method, and line from the stack
     * returns a string array with element 0 as file name and
     * element 1 as method[line]
     */
    public static String[] trace(final StackTraceElement e[], final int level) {
        if (e != null && e.length >= level) {
            final StackTraceElement s = e[level];
            if (s != null) {
                return new String[] {
                        e[level].getFileName(), e[level].getLineNumber() + ""
                };
            }
        }
        return null;
    }

}
