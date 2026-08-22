package ir.codemarket.app

import java.text.SimpleDateFormat
import java.util.*

object TimeUtils {
    fun getTimeAgo(dateString: String?): String {
        if (dateString.isNullOrEmpty()) return "همین الان"
        try {
            // فرمت دقیق تاریخی که از Node.js ارسال میشه
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
            format.timeZone = TimeZone.getTimeZone("UTC")
            val pastDate = format.parse(dateString) ?: return "همین الان"
            
            val now = Date()
            val seconds = (now.time - pastDate.time) / 1000

            return when {
                seconds < 0 -> "همین الان" // جلوگیری از باگ اختلاف ساعت سرور و کلاینت
                seconds < 60 -> "همین الان"
                seconds < 3600 -> "${seconds / 60} دقیقه پیش"
                seconds < 86400 -> "${seconds / 3600} ساعت پیش"
                seconds < 172800 -> "دیروز"
                seconds < 2592000 -> "${seconds / 86400} روز پیش"
                seconds < 31104000 -> "${seconds / 2592000} ماه پیش"
                else -> "${seconds / 31104000} سال پیش"
            }
        } catch (e: Exception) {
            return "نامشخص"
        }
    }
}