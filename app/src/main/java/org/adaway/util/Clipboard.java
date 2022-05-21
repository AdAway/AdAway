package org.adaway.util;

import static android.widget.Toast.LENGTH_SHORT;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

import org.adaway.R;

/**
 * This class manages the clipboard interactions.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public final class Clipboard {
    private Clipboard() {

    }

    /**
     * Copy a host into the clipboard for the user.
     *
     * @param context The application context.
     * @param host    The host to copy to the clipboard
     */
    public static void copyHostToClipboard(Context context, String host) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = ClipData.newPlainText("Host", host);
        clipboard.setPrimaryClip(clipData);
        Toast.makeText(
                context,
                context.getString(R.string.clipboard_host_copied),
                LENGTH_SHORT
        ).show();
    }
}
