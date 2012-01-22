package org.adaway.ui;

import android.app.Application;

public class AdAwayApplication extends Application {

    @Override
    public void onCreate() {

        // workaround for http://code.google.com/p/android/issues/detail?id=20915
        try {
            Class.forName("android.os.AsyncTask");
        } catch (ClassNotFoundException e) {
        }

        super.onCreate();
    }

}
