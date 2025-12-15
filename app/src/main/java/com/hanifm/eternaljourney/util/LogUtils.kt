package com.hanifm.eternaljourney.util

import android.util.Log
import com.hanifm.eternaljourney.BuildConfig

object LogUtils {
    private const val TAG_PREFIX = Constants.LOG_TAG
    
    /**
     * Debug logs - always enabled for troubleshooting
     * Use for detailed flow tracking and state information
     */
    fun d(tag: String, message: String) {
        Log.d("$TAG_PREFIX$tag", message)
    }
    
    /**
     * Info logs - always enabled for important events
     * Use for significant app events (connections, playback start/stop, etc.)
     */
    fun i(tag: String, message: String) {
        Log.i("$TAG_PREFIX$tag", message)
    }
    
    /**
     * Warning logs - always enabled
     * Use for unexpected but recoverable situations
     */
    fun w(tag: String, message: String) {
        Log.w("$TAG_PREFIX$tag", message)
    }
    
    /**
     * Error logs - always enabled
     * Use for errors and exceptions
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e("$TAG_PREFIX$tag", message, throwable)
        } else {
            Log.e("$TAG_PREFIX$tag", message)
        }
    }
}
