package mufanc.tools.aphelper

import android.util.Log

object Logger {
    private const val TAG = "HotspotFix"

    fun i(vararg args: Any?) {
        Log.i(TAG, args.joinToString(" ") { "$it" })
    }

    fun e(err: Throwable) {
        Log.e(TAG, "", err)
    }

    fun e(vararg args: Any?) {
        Log.e(TAG, args.joinToString(" ") { "$it" })
    }
}