package mme.corp.audioshare.logging

import android.util.Log

interface AppLogger {

    fun debug(tag: String, message: String)

    fun info(tag: String, message: String)

    fun warn(
        tag: String,
        message: String,
        throwable: Throwable? = null
    )

    fun error(
        tag: String,
        message: String,
        throwable: Throwable? = null
    )

    companion object {
        val NO_OP: AppLogger = object : AppLogger {
            override fun debug(tag: String, message: String) = Unit

            override fun info(tag: String, message: String) = Unit

            override fun warn(
                tag: String,
                message: String,
                throwable: Throwable?
            ) = Unit

            override fun error(
                tag: String,
                message: String,
                throwable: Throwable?
            ) = Unit
        }
    }
}

object AndroidAppLogger : AppLogger {

    override fun debug(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun info(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun warn(
        tag: String,
        message: String,
        throwable: Throwable?
    ) {
        if (throwable == null) {
            Log.w(tag, message)
        } else {
            Log.w(tag, message, throwable)
        }
    }

    override fun error(
        tag: String,
        message: String,
        throwable: Throwable?
    ) {
        if (throwable == null) {
            Log.e(tag, message)
        } else {
            Log.e(tag, message, throwable)
        }
    }
}
