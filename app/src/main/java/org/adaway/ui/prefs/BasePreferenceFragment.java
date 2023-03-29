package org.adaway.ui.prefs;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import org.adaway.R;
import java.util.Arrays;
import java.util.Objects;

public class BasePreferenceFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(@Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState, @Nullable @org.jetbrains.annotations.Nullable String rootKey) {}

    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        if (preference instanceof ListPreference) {
            showListPreference((ListPreference) preference);
        } else if (preference instanceof EditTextPreference) {
            showEditTextPreference((EditTextPreference) preference);
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    public void showListPreference(@NonNull ListPreference preference) {
        int selectionIndex = Arrays.asList(preference.getEntryValues()).indexOf(preference.getValue());
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle(preference.getTitle());
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.setSingleChoiceItems(preference.getEntries(), selectionIndex, (dialog, index) -> {
            String newValue = preference.getEntryValues()[index].toString();
            if (preference.callChangeListener(newValue)) {
                preference.setValue(newValue);
            }
            dialog.dismiss();
        });
        builder.show();
    }

    public void showEditTextPreference(@NonNull EditTextPreference preference) {
        View dialogView = View.inflate(getContext(), R.layout.dialog_edit_text, null);
        TextInputEditText input = dialogView.findViewById(R.id.textInput);
        input.setText(preference.getText());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle(preference.getTitle());
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.setPositiveButton(android.R.string.ok, (dialog, i) -> {
            String newValue = Objects.requireNonNull(input.getText()).toString();
            if (preference.callChangeListener(newValue)) {
                preference.setText(newValue);
            }
            dialog.dismiss();
        });
        builder.setView(dialogView);
        builder.show();
    }
}