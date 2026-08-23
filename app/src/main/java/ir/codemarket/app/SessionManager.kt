package ir.codemarket.app

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("CodeMarketPrefs", Context.MODE_PRIVATE)

    fun saveAuthToken(token: String) = prefs.edit().putString("AUTH_TOKEN", token).apply()
    fun fetchAuthToken(): String? = prefs.getString("AUTH_TOKEN", null)

    fun saveUsername(username: String) = prefs.edit().putString("USERNAME", username).apply()

    fun saveThemeMode(isDark: Boolean) = prefs.edit().putBoolean("IS_DARK", isDark).apply()
    fun isDarkMode(): Boolean = prefs.getBoolean("IS_DARK", true)

    fun saveTextSize(size: Float) = prefs.edit().putFloat("TEXT_SIZE", size).apply()
    fun getTextSize(): Float = prefs.getFloat("TEXT_SIZE", 15f) // سایز پیش‌فرض 15sp
}