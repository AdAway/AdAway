package org.adaway.util

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar

object ExpressiveSnackbar {
    @JvmStatic
    fun style(snackbar: Snackbar) {
        val snackbarView = snackbar.view
        val context = snackbarView.context
        val density = context.resources.displayMetrics.density

        // 1. Margins (floating layout)
        val params = snackbarView.layoutParams
        if (params is ViewGroup.MarginLayoutParams) {
            params.setMargins(
                (16 * density).toInt(),
                0,
                (16 * density).toInt(),
                (24 * density).toInt() // position it higher up above system navigation bar
            )
            snackbarView.layoutParams = params
        }

        // 2. Shape and Background
        val r1 = 20 * density
        val r2 = 4 * density
        val radii = floatArrayOf(
            r1, r1, // top-left
            r2, r2, // top-right
            r1, r1, // bottom-right
            r1, r1  // bottom-left
        )
        
        val typedValueBg = TypedValue()
        val typedValueText = TypedValue()
        val typedValuePrimary = TypedValue()

        val hasBg = context.theme.resolveAttribute(
            com.google.android.material.R.attr.colorSurfaceContainerHigh,
            typedValueBg,
            true
        )
        val hasText = context.theme.resolveAttribute(
            com.google.android.material.R.attr.colorOnSurface,
            typedValueText,
            true
        )
        val hasPrimary = context.theme.resolveAttribute(
            android.R.attr.colorPrimary,
            typedValuePrimary,
            true
        )

        val bgColor = if (hasBg) typedValueBg.data else 0xFF322F37.toInt()
        val textColor = if (hasText) typedValueText.data else 0xFFF5EFF7.toInt()
        val primaryColor = if (hasPrimary) typedValuePrimary.data else 0xFF6750A4.toInt()

        val backgroundDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = radii
            setColor(bgColor)
            setStroke((1.5f * density).toInt(), primaryColor)
        }

        snackbarView.background = backgroundDrawable
        snackbarView.elevation = 8 * density

        // 3. Text styling
        val textView = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView?.let {
            it.setTextColor(textColor)
            it.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            it.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        // 4. Action button styling
        val actionButton = snackbarView.findViewById<Button>(com.google.android.material.R.id.snackbar_action)
        actionButton?.let {
            it.setTextColor(primaryColor)
            it.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
    }
}
