package com.example.tracker.data

import android.content.Context

/** Keeps the id of the session currently being recorded (survives process death). */
object SessionManager {
    private const val PREFS = "tracker_state"
    private const val KEY_CURRENT = "current_session"
    private const val KEY_VIEW = "view_session"

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun current(c: Context): Long = prefs(c).getLong(KEY_CURRENT, 0L)

    fun setCurrent(c: Context, id: Long) {
        prefs(c).edit().putLong(KEY_CURRENT, id).apply()
    }

    fun clearCurrent(c: Context) {
        prefs(c).edit().remove(KEY_CURRENT).apply()
    }

    /** Which session the map should display when nothing is being recorded. */
    fun viewSession(c: Context): Long = prefs(c).getLong(KEY_VIEW, 0L)

    fun setViewSession(c: Context, id: Long) {
        prefs(c).edit().putLong(KEY_VIEW, id).apply()
    }
}
