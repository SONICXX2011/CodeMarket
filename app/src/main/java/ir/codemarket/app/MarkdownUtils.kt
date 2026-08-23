package ir.codemarket.app

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText

object MarkdownUtils {
    fun applyMarkdownShortcuts(editText: EditText) {
        editText.customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.add(0, 1, 0, "پررنگ (Bold)")
                menu.add(0, 2, 0, "نقل قول")
                menu.add(0, 3, 0, "کد (Code)")
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

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