package ir.codemarket.app

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText

object MarkdownUtils {
    fun applyMarkdownShortcuts(editText: EditText) {
        editText.customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                // این فلگ‌ها باعث میشن تو همه گوشی‌ها (مخصوصاً سامسونگ) دکمه‌ها حتماً نمایش داده بشن
                menu.add(Menu.NONE, 1, 1, "پررنگ").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
                menu.add(Menu.NONE, 2, 2, "نقل قول").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
                menu.add(Menu.NONE, 3, 3, "کد").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                return true // اینجا حتماً باید true باشه تا منو آپدیت و نشون داده بشه
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                val start = editText.selectionStart
                val end = editText.selectionEnd
                if (start < 0 || end < 0 || start == end) return false

                val text = editText.text

                when (item.itemId) {
                    1 -> { // Bold
                        text.insert(end, "**")
                        text.insert(start, "**")
                        mode.finish()
                        return true
                    }
                    2 -> { // Quote
                        text.insert(start, "> ")
                        mode.finish()
                        return true
                    }
                    3 -> { // Code
                        text.insert(end, "`")
                        text.insert(start, "`")
                        mode.finish()
                        return true
                    }
                }
                return false
            }

            override fun onDestroyActionMode(mode: ActionMode) {}
        }
    }
}