package org.adaway.ui.lists;

import android.content.Context;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.adaway.R;

import java.util.function.Consumer;

import static android.view.LayoutInflater.from;
import static org.adaway.ui.lists.ListsFilter.ALL;

/**
 * This class is the hosts list search dialog.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public final class ListsFilterDialog {
    /**
     * Display the hosts search dialog.
     *
     * @param context       The dialog context.
     * @param currentFilter The current filter to set initial dialog values.
     * @param callback      The callback to call on filter change.
     */
    public static void show(Context context, ListsFilter currentFilter, Consumer<ListsFilter> callback) {
        View view = from(context).inflate(R.layout.list_filter_dialog, null);
        AlertDialog dialog = createDialog(context, view, callback);
        setValues(view, currentFilter);
        dialog.show();
    }

    private static AlertDialog createDialog(Context context, View view, Consumer<ListsFilter> callback) {
        return new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.lists_menu_filter)
                .setView(view)
                .setPositiveButton(
                        R.string.lists_filter_apply,
                        (dialog, which) -> {
                            ListsFilter filter = getDialogFilter((AlertDialog) dialog);
                            callback.accept(filter);
                        }
                )
                .setNegativeButton(
                        R.string.lists_filter_reset,
                        (dialog, which) -> callback.accept(ALL))
                .create();
    }

    private static void setValues(View view, ListsFilter currentFilter) {
        EditText hostEditText = view.findViewById(R.id.lists_filter_host);
        if (hostEditText != null) {
            hostEditText.setText(currentFilter.hostFilter);
        }
        CheckBox sourceIncludedCheckBox = view.findViewById(R.id.lists_filter_sources_included);
        if (sourceIncludedCheckBox != null) {
            sourceIncludedCheckBox.setChecked(currentFilter.sourcesIncluded);
        }
    }

    private static ListsFilter getDialogFilter(AlertDialog dialog) {
        EditText hostEditText = dialog.findViewById(R.id.lists_filter_host);
        CheckBox sourceIncludedCheckBox = dialog.findViewById(R.id.lists_filter_sources_included);
        if (hostEditText == null || sourceIncludedCheckBox == null) {
            return ALL;
        }
        return new ListsFilter(sourceIncludedCheckBox.isChecked(), hostEditText.getText().toString());
    }
}
