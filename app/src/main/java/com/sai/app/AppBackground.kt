package com.sai.app

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View

/** Persists and applies a user-chosen app background: the default, a solid color, or a picture. */
object AppBackground {
    private const val PREFS_NAME = "app_background"
    private const val KEY_TYPE = "type"
    private const val KEY_COLOR = "color"
    private const val KEY_IMAGE_URI = "image_uri"

    private const val TYPE_DEFAULT = "default"
    private const val TYPE_COLOR = "color"
    private const val TYPE_IMAGE = "image"

    fun setColor(context: Context, color: Int) {
        prefs(context).edit()
            .putString(KEY_TYPE, TYPE_COLOR)
            .putInt(KEY_COLOR, color)
            .apply()
    }

    fun setImage(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // Grant couldn't be persisted; the picture still applies this session.
        }
        prefs(context).edit()
            .putString(KEY_TYPE, TYPE_IMAGE)
            .putString(KEY_IMAGE_URI, uri.toString())
            .apply()
    }

    fun resetToDefault(context: Context) {
        prefs(context).edit().putString(KEY_TYPE, TYPE_DEFAULT).apply()
    }

    /** Applies the stored choice onto [root]; leaves the view's existing background untouched if none was chosen. */
    fun apply(context: Context, root: View) {
        val p = prefs(context)
        when (p.getString(KEY_TYPE, TYPE_DEFAULT)) {
            TYPE_COLOR -> root.setBackgroundColor(p.getInt(KEY_COLOR, Color.BLACK))
            TYPE_IMAGE -> {
                val raw = p.getString(KEY_IMAGE_URI, null) ?: return
                try {
                    val uri = Uri.parse(raw)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bitmap = BitmapFactory.decodeStream(input)
                        if (bitmap != null) {
                            root.background = BitmapDrawable(context.resources, bitmap).apply {
                                gravity = Gravity.FILL
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Picture no longer accessible (revoked permission, deleted file); keep whatever's there.
                }
            }
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
