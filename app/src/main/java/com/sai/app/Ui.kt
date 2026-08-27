package com.sai.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Shared FL Mobile chrome: headers, cards, chips, list rows, inputs, dialogs. */
object Ui {

    fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun drawable(context: Context, id: Int, alpha: Int? = null) =
        ContextCompat.getDrawable(context, id)?.mutate()?.also { drawn ->
            if (alpha != null) drawn.alpha = alpha
        }

    fun screen(context: Context, padDp: Float = 12f, setup: LinearLayout.() -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, padDp), dp(context, padDp), dp(context, padDp), dp(context, padDp))
            setBackgroundColor(AppTheme.canvas)
            setup()
        }

    fun headerBar(context: Context, setup: LinearLayout.() -> Unit): LinearLayout {
        val density = context.resources.displayMetrics.density
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = drawable(context, R.drawable.header_bar_bg)
            setPadding(dp(context, 10f), dp(context, 6f), dp(context, 8f), dp(context, 6f))
            elevation = 4f * density
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(context, 8f) }
            setup()
        }
    }

    fun screenTitle(context: Context, text: String, monospace: Boolean = true): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(AppTheme.accentColor(context))
            textSize = AppTheme.TYPE_SCREEN
            typeface = if (monospace) Typeface.MONOSPACE else Typeface.DEFAULT_BOLD
            letterSpacing = 0.04f
        }

    fun identityColumn(context: Context, title: String, meta: String, onTitleClick: (() -> Unit)? = null): LinearLayout {
        val titleView = TextView(context).apply {
            this.text = title
            setTextColor(AppTheme.textPrimary)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
            if (onTitleClick != null) setOnClickListener { onTitleClick() }
        }
        val metaView = TextView(context).apply {
            this.text = meta
            setTextColor(AppTheme.textSecondary)
            textSize = AppTheme.TYPE_MICRO
            typeface = Typeface.MONOSPACE
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleView)
            addView(metaView)
            tag = arrayOf(titleView, metaView)
        }
    }

    fun meta(context: Context, text: String = ""): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(AppTheme.textSecondary)
            textSize = AppTheme.TYPE_META
            typeface = Typeface.MONOSPACE
        }

    fun hint(context: Context, text: String): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(AppTheme.textMuted)
            textSize = AppTheme.TYPE_MICRO
        }

    fun body(context: Context, text: String): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(AppTheme.textPrimary)
            textSize = AppTheme.TYPE_BODY
        }

    fun sectionLabel(context: Context, text: String): TextView =
        TextView(context).apply {
            this.text = text.uppercase()
            setTextColor(AppTheme.accentColor(context))
            textSize = AppTheme.TYPE_MICRO
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.08f
            setPadding(0, dp(context, 8f), 0, dp(context, 4f))
        }

    fun divider(context: Context): View =
        View(context).apply {
            setBackgroundColor(AppTheme.border)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 1f),
            ).apply {
                topMargin = dp(context, 8f)
                bottomMargin = dp(context, 8f)
            }
        }

    fun emptyState(context: Context, text: String): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(AppTheme.textMuted)
            textSize = AppTheme.TYPE_META
            setPadding(dp(context, 12f), dp(context, 16f), dp(context, 12f), dp(context, 16f))
            background = drawable(context, R.drawable.list_row_bg)
        }

    fun card(context: Context, padDp: Float = 8f, setup: LinearLayout.() -> Unit = {}): LinearLayout {
        val density = context.resources.displayMetrics.density
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = drawable(context, R.drawable.module_card_bg)
            setPadding(dp(context, padDp), dp(context, padDp), dp(context, padDp), dp(context, padDp))
            elevation = 6f * density
            setup()
        }
    }

    fun listRow(
        context: Context,
        title: String,
        subtitle: String? = null,
        leading: Int? = null,
        trailing: String? = null,
        onClick: (() -> Unit)? = null,
        onLongClick: (() -> Boolean)? = null,
    ): LinearLayout {
        val density = context.resources.displayMetrics.density
        val titleView = TextView(context).apply {
            text = title
            setTextColor(AppTheme.textPrimary)
            textSize = AppTheme.TYPE_BODY
            typeface = Typeface.DEFAULT_BOLD
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(titleView)
            if (!subtitle.isNullOrBlank()) {
                addView(TextView(context).apply {
                    text = subtitle
                    setTextColor(AppTheme.textSecondary)
                    textSize = AppTheme.TYPE_MICRO
                    isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.END
                })
            }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = drawable(context, R.drawable.list_row_bg)
            minimumHeight = dp(context, 44f)
            setPadding(0, dp(context, 4f), dp(context, 10f), dp(context, 4f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(context, 6f) }
            elevation = 2f * density
            if (leading != null) {
                addView(
                    View(context).apply { setBackgroundColor(leading) },
                    LinearLayout.LayoutParams(dp(context, 4f), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                        marginEnd = dp(context, 10f)
                    },
                )
            } else {
                setPadding(dp(context, 12f), dp(context, 8f), dp(context, 10f), dp(context, 8f))
            }
            addView(textCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (!trailing.isNullOrBlank()) {
                addView(chipLabel(context, trailing))
            }
            if (onClick != null) {
                isClickable = true
                setOnClickListener { onClick() }
            }
            if (onLongClick != null) {
                isLongClickable = true
                setOnLongClickListener { onLongClick() }
            }
        }
    }

    fun chipLabel(context: Context, text: String, selected: Boolean = false): TextView =
        TextView(context).apply {
            this.text = text
            textSize = AppTheme.TYPE_MICRO
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(dp(context, 8f), dp(context, 3f), dp(context, 8f), dp(context, 3f))
            styleChip(this, selected)
        }

    fun styleChip(view: TextView, selected: Boolean, selectedColor: Int? = null) {
        val context = view.context
        val lit = selectedColor ?: AppTheme.accentColor(context)
        view.setTextColor(if (selected) AppTheme.canvas else AppTheme.textSecondary)
        view.background = GradientDrawable().apply {
            cornerRadius = 999f
            setColor(if (selected) lit else AppTheme.surfaceRaised)
            setStroke(dp(context, 1f), if (selected) lit else AppTheme.border)
        }
    }

    fun compactButton(
        context: Context,
        label: String,
        onClick: () -> Unit,
        selected: Boolean = false,
    ): Button {
        return Button(context).apply {
            text = label
            textSize = AppTheme.TYPE_CHIP
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(context, 10f), dp(context, 6f), dp(context, 10f), dp(context, 6f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(dp(context, 3f), 0, dp(context, 3f), 0) }
            includeFontPadding = false
            elevation = 0f
            stateListAnimator = null
            styleCompact(this, selected)
            setOnClickListener { onClick() }
        }.also { it.elevation = 0f }
    }

    fun styleCompact(button: Button, selected: Boolean, selectedColor: Int? = null) {
        val context = button.context
        val lit = selectedColor ?: AppTheme.accentColor(context)
        button.setTextColor(if (selected) AppTheme.canvas else AppTheme.textPrimary)
        button.background = GradientDrawable().apply {
            cornerRadius = 8f * context.resources.displayMetrics.density
            setColor(if (selected) lit else AppTheme.surfaceRaised)
            setStroke(
                dp(context, 1f),
                if (selected) lit else AppTheme.border,
            )
        }
        button.elevation = 0f
        button.stateListAnimator = null
    }

    fun chromeButton(context: Context, label: String, onClick: () -> Unit): TextView {
        val density = context.resources.displayMetrics.density
        return TextView(context).apply {
            text = label
            setTextColor(AppTheme.textPrimary)
            textSize = AppTheme.TYPE_CHIP
            gravity = Gravity.CENTER
            setPadding(dp(context, 10f), dp(context, 5f), dp(context, 10f), dp(context, 5f))
            background = drawable(context, R.drawable.chrome_button_bg)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = (4 * density).toInt() }
            setOnClickListener { onClick() }
        }
    }

    fun styleSelectedChrome(view: TextView, selected: Boolean) {
        val context = view.context
        view.setTextColor(if (selected) AppTheme.canvas else AppTheme.textPrimary)
        if (selected) {
            view.background = GradientDrawable().apply {
                cornerRadius = 8f * context.resources.displayMetrics.density
                setColor(AppTheme.accentColor(context))
            }
        } else {
            view.background = drawable(context, R.drawable.chrome_button_bg)
        }
    }

    fun input(context: Context, hint: String = "", text: String = ""): EditText =
        EditText(context).apply {
            this.hint = hint
            setText(text)
            setTextColor(AppTheme.textPrimary)
            setHintTextColor(AppTheme.textMuted)
            textSize = AppTheme.TYPE_BODY
            background = drawable(context, R.drawable.input_bg)
            setPadding(dp(context, 12f), dp(context, 8f), dp(context, 12f), dp(context, 8f))
            setSingleLine()
        }

    fun dialog(context: Context): AlertDialog.Builder =
        AlertDialog.Builder(context, R.style.Theme_Sai_Dialog)

    fun styleDialogContent(view: View) {
        view.setBackgroundColor(AppTheme.surface)
        if (view is LinearLayout) {
            view.setPadding(
                view.paddingLeft.coerceAtLeast(dp(view.context, 8f)),
                view.paddingTop.coerceAtLeast(dp(view.context, 8f)),
                view.paddingRight.coerceAtLeast(dp(view.context, 8f)),
                view.paddingBottom.coerceAtLeast(dp(view.context, 8f)),
            )
        }
    }

    fun accentStrip(context: Context, color: Int = AppTheme.accentColor(context)): View =
        View(context).apply {
            setBackgroundColor(color)
            layoutParams = LinearLayout.LayoutParams(dp(context, 3f), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                marginEnd = dp(context, 10f)
            }
        }

    fun navPills(context: Context, vararg letters: Pair<String, () -> Unit>): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            for ((letter, onClick) in letters) {
                addView(PillButton.create(context, letter, onClick))
            }
        }
}
