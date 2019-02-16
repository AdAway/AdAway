package org.adaway.ui.dialog;


import androidx.appcompat.app.AlertDialog;
import androidx.arch.core.util.Function;

import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;

/**
 * This class is a {@link TextWatcher} to validate an alert dialog field.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class AlertDialogValidator implements TextWatcher {
    /**
     * The button to change status.
     */
    private final Button mButton;
    /**
     * The field validator.
     */
    private final Function<String, Boolean> validator;

    /**
     * Constructor.
     *
     * @param dialog       The button to change status.
     * @param validator    The field validator.
     * @param initialState The validation initial state.
     */
    public AlertDialogValidator(AlertDialog dialog, Function<String, Boolean> validator, boolean initialState) {
        this.mButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        this.mButton.setEnabled(initialState);
        this.validator = validator;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    @Override
    public void afterTextChanged(Editable s) {
        String url = s.toString();
        this.mButton.setEnabled(this.validator.apply(url));
    }
}
