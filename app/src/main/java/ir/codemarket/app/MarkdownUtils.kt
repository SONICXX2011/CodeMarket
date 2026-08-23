package ir.codemarket.app

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText

object MarkdownUtils {
    fun applyMarkdownShortcuts(editText: EditText) {
        editText.customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                // فلگ SHOW_AS_ACTION_ALWAYS باعث می‌شود گوشی‌های سامسونگ این موارد را حتماً در نوار شناور نشان دهند
                menu.add(Menu.NONE, 1, Menu.NONE, "پررنگ").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
                menu.add(Menu.NONE, 2, Menu.NONE, "نقل قول").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
                menu.add(Menu.NONE, 3, Menu.NONE, "کد").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                return false
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                val start = editText.selectionStart
                val end = editText.selectionEnd
                
                if (start < 0 || end < 0) return false
                
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