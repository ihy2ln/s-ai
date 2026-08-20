package com.sai.app

import android.content.Context
import android.app.AlertDialog

/** Project admin menu opened from the **P** pill button. */
object ProjectMenu {

    data class Actions(
        val onRename: () -> Unit,
        val onSave: () -> Unit,
        val onLoad: () -> Unit,
        val onNew: () -> Unit,
        val onUndo: () -> Unit,
        val onRedo: () -> Unit,
    )

    fun show(context: Context, actions: Actions) {
        AlertDialog.Builder(context)
            .setTitle("Project")
            .setItems(arrayOf("Rename", "Save", "Load", "New", "Undo", "Redo")) { _, which ->
                when (which) {
                    0 -> actions.onRename()
                    1 -> actions.onSave()
                    2 -> actions.onLoad()
                    3 -> actions.onNew()
                    4 -> actions.onUndo()
                    5 -> actions.onRedo()
                }
            }
            .show()
    }
}
