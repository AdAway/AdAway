package org.adaway.ui.hostsdiff;

import androidx.annotation.NonNull;

interface CallbackInterface {

    void showProgressBar(int progress, int max);

    void hideProgressBar();

    void setResult(@NonNull String string);
}
