package ir.codemarket.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.TextView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme

object MarkdownUtils {

    // ساخت یک موتور مارک‌داون پیشرفته که رنگ‌ها و کلیک‌ها رو رهگیری می‌کنه
    fun createMarkwon(context: Context): Markwon {
        return Markwon.builder(context)
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    // رنگ آبی مخصوص لینک‌ها و آیدی‌ها
                    builder.linkColor(Color.parseColor("#5288C1"))
                }

                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver { _, link ->
                        // اگر لینک، یک آیدی کاربر بود، ببرش تو صفحه پروفایل
                        if (link.startsWith("codemarket://user/")) {
                            val username = link.replace("codemarket://user/", "")
                            val intent = Intent(context, UserProfileActivity::class.java)
                            intent.putExtra("target_username", username)
                            context.startActivity(intent)
                        } else {
                            // در غیر این صورت لینک رو توی مرورگر باز کن
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                                context.startActivity(intent)
                            } catch (e: Exception) { }
                        }
                    }
                }
            })
            .build()
    }

    // این متد متن خام رو می‌گیره، خودش آیدی‌ها و لینک‌ها رو هوشمند تشخیص میده و لینک‌دار می‌کنه
    fun setMarkdownText(markwon: Markwon, textView: TextView, text: String) {
        var processed = text
        
        // ۱. پیدا کردن آیدی‌ها (مثل @ali) و تبدیلشون به لینک داخلی (اگه از قبل داخل کروشه مارک‌داون نباشن)
        val mentionRegex = Regex("(?<!\\[)@([A-Za-z0-9_]+)(?!\\])")
        processed = processed.replace(mentionRegex, "[@$1](codemarket://user/$1)")
        
        // ۲. پیدا کردن لینک‌های سایت‌ها و تبدیلشون به لینک آبی قابل کلیک
        val urlRegex = Regex("(?<!\\]\\()\\b(https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])")
        processed = processed.replace(urlRegex, "[$1]($1)")

        // رندر کردن در TextView
        markwon.setMarkdown(textView, processed)
    }

    fun applyMarkdownShortcuts(editText: EditText) {
        editText.customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.add(Menu.NONE, 1, 1, "پررنگ").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
                menu.add(Menu.NONE, 2, 2, "نقل قول").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
                menu.add(Menu.NONE, 3, 3, "کد").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = true

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                val start = editText.selectionStart
                val end = editText.selectionEnd
                if (start < 0 || end < 0 || start == end) return false

                val text = editText.text

                when (item.itemId) {
                    1 -> { text.insert(end, "**"); text.insert(start, "**"); mode.finish(); return true }
                    2 -> { text.insert(start, "> "); mode.finish(); return true }
                    3 -> { text.insert(end, "`"); text.insert(start, "`"); mode.finish(); return true }
                }
                return false
            }

            override fun onDestroyActionMode(mode: ActionMode) {}
        }
    }
}