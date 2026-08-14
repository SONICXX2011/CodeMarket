package ir.codemarket.app

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("CodeMarketPrefs", Context.MODE_PRIVATE)
    fun saveAuthToken(token: String) { prefs.edit().putString("jwt_token", token).apply() }
    fun fetchAuthToken(): String? = prefs.getString("jwt_token", null)
    fun saveUsername(username: String) { prefs.edit().putString("username", username).apply() }
    fun fetchUsername(): String? = prefs.getString("username", null)
    fun saveThemeMode(isDark: Boolean) { prefs.edit().putBoolean("is_dark", isDark).apply() }
    fun isDarkMode(): Boolean = prefs.getBoolean("is_dark", true)
    fun logout() { prefs.edit().clear().apply() }
}