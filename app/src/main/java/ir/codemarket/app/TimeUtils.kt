package ir.codemarket.app

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

object TimeUtils {
    fun getTimeAgo(isoString: String): String {
        return try {
            val format = if (isoString.contains(".")) {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.getDefault())
            } else {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            }
            val date = format.parse(isoString)
            val diffInMillis = System.currentTimeMillis() - date.time
            
            val seconds = TimeUnit.MILLISECONDS.toSeconds(diffInMillis)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diffInMillis)
            val hours = TimeUnit.MILLISECONDS.toHours(diffInMillis)
            val days = TimeUnit.MILLISECONDS.toDays(diffInMillis)

            when {
                seconds < 60 -> "همین الان"
                minutes < 60 -> "$minutes دقیقه پیش"
                hours < 24 -> "$hours ساعت پیش"
                days < 7 -> "$days روز پیش"
                else -> SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(date)
            }
        } catch (e: Exception) {
            ""
        }
    }
}