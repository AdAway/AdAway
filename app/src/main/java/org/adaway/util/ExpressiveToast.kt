package org.adaway.util

import android.content.Context
import android.view.Gravity
import android.widget.Toast
import androidx.annotation.StringRes

object ExpressiveToast {

    @JvmStatic
    fun makeText(context: Context, @StringRes resId: Int, duration: Int): Toast {
        return makeText(context, context.getString(resId), duration)
    }

    @JvmStatic
    fun makeText(context: Context, text: CharSequence, duration: Int): Toast {
        val density = context.resources.displayMetrics.density
        val toast = Toast.makeText(context, text, duration)
        toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, (80 * density).toInt())
        return toast
    }
}
