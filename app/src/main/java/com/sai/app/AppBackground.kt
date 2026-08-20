package com.sai.app

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.MediaPlayer
import android.net.Uri
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.VideoView

/**
 * Persists and applies a user-chosen app background: the default dark color, a solid color, a
 * picture, or a looping muted video (any format the device's decoder supports - mp4/webm/mkv/3gp
 * etc, same as any other Android video playback). Optionally mirrored, and its opacity is a
 * dark scrim over the picture/video rather than a destructive edit - turning it down never
 * clears the chosen background, it just reveals more of it.
 */
object AppBackground {
    private const val PREFS_NAME = "app_background"
    private const val KEY_TYPE = "type"
    private const val KEY_COLOR = "color"
    private const val KEY_IMAGE_URI = "image_uri"
    private const val KEY_VIDEO_URI = "video_uri"
    private const val KEY_MIRROR = "mirror"

    private const val TYPE_DEFAULT = "default"
    private const val TYPE_COLOR = "color"
    private const val TYPE_IMAGE = "image"
    private const val TYPE_VIDEO = "video"

    private val BASE_COLOR = Color.rgb(18, 18, 20)

    fun setColor(context: Context, color: Int) {
        prefs(context).edit()
            .putString(KEY_TYPE, TYPE_COLOR)
            .putInt(KEY_COLOR, color)
            .apply()
    }

    fun setImage(context: Context, uri: Uri) {
        persistPermission(context, uri)
        prefs(context).edit()
            .putString(KEY_TYPE, TYPE_IMAGE)
            .putString(KEY_IMAGE_URI, uri.toString())
            .apply()
    }

    fun setVideo(context: Context, uri: Uri) {
        persistPermission(context, uri)
        prefs(context).edit()
            .putString(KEY_TYPE, TYPE_VIDEO)
            .putString(KEY_VIDEO_URI, uri.toString())
            .apply()
    }

    fun mirrorEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_MIRROR, false)

    fun setMirrorEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MIRROR, enabled).apply()
    }

    fun resetToDefault(context: Context) {
        prefs(context).edit().putString(KEY_TYPE, TYPE_DEFAULT).putBoolean(KEY_MIRROR, false).apply()
    }

    /** Wraps [content] with the chosen background layered behind it; call this instead of
     *  setting content's own background, and pass the result to setContentView. */
    fun wrap(context: Context, content: View): View {
        SystemBarInsets.applyPadding(content)
        val p = prefs(context)
        val type = p.getString(KEY_TYPE, TYPE_DEFAULT)
        val mirror = mirrorEnabled(context)

        val backgroundLayer: View? = when (type) {
            TYPE_COLOR -> View(context).apply { setBackgroundColor(p.getInt(KEY_COLOR, BASE_COLOR)) }
            TYPE_IMAGE -> buildImageLayer(context, p.getString(KEY_IMAGE_URI, null), mirror)
            TYPE_VIDEO -> buildVideoLayer(context, p.getString(KEY_VIDEO_URI, null), mirror)
            else -> null
        }

        if (backgroundLayer == null) {
            content.setBackgroundColor(BASE_COLOR)
            return content
        }

        val scrim = View(context).apply {
            setBackgroundColor(BASE_COLOR)
            alpha = AppTheme.backgroundOpacityPercent(context) / 100f
        }
        content.setBackgroundColor(Color.TRANSPARENT)

        return FrameLayout(context).apply {
            addView(backgroundLayer, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(scrim, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
    }

    private fun buildImageLayer(context: Context, rawUri: String?, mirror: Boolean): View? {
        val raw = rawUri ?: return null
        return try {
            val uri = Uri.parse(raw)
            val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
            val shown = if (mirror) {
                val matrix = Matrix().apply { preScale(-1f, 1f) }
                android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(shown)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun buildVideoLayer(context: Context, rawUri: String?, mirror: Boolean): View? {
        val raw = rawUri ?: return null
        val uri = try {
            Uri.parse(raw)
        } catch (e: Exception) {
            return null
        }
        return VideoView(context).apply {
            if (mirror) scaleX = -1f
            setVideoURI(uri)
            setOnPreparedListener { player: MediaPlayer ->
                player.isLooping = true
                player.setVolume(0f, 0f)
                player.start()
            }
            setOnErrorListener { _, _, _ -> true }
        }
    }

    private fun persistPermission(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // Grant couldn't be persisted; the background still applies this session.
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
