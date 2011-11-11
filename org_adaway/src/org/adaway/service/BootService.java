package org.adaway.service;

import org.adaway.helper.PreferencesHelper;
import org.adaway.util.WebserverUtils;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

public class BootService extends IntentService {
    private Context mApplicationContext;

    public BootService() {
        super("AdAwayOnBootService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        mApplicationContext = getApplicationContext();

        // schedule UpdateCheckService
        UpdateCheckService.registerAlarm(mApplicationContext);

        // start webserver on boot if enabled in preferences
        if (PreferencesHelper.getWebserverOnBoot(mApplicationContext)) {
            // Wait a little bit before starting webserver
            try {
                Thread.sleep(5 * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            WebserverUtils.startWebserver(mApplicationContext);
        }
    }

}
