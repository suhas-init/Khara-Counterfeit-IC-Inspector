package com.khara.prototype.util

import android.content.Context

object UserProfileStore {
    private const val PREFS = "khara_users"
    private const val KEY_LIST = "user_list"
    private const val KEY_LAST = "last_user"

    fun getAllUsers(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LIST, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split("|").filter { it.isNotBlank() }
    }

    fun addUser(context: Context, name: String) {
        val users = getAllUsers(context).toMutableList()
        if (!users.contains(name)) users.add(name)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LIST, users.joinToString("|")).apply()
    }

    fun getLastUser(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST, null)

    fun setLastUser(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST, name).apply()
    }
}
