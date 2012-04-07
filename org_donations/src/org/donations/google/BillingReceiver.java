/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.donations.google;

import org.donations.google.Consts.ResponseCode;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * This class implements the broadcast receiver for in-app billing. All asynchronous messages from
 * Android Market come to this app through this receiver. This class forwards all messages to the
 * {@link BillingService}, which can start background threads, if necessary, to process the
 * messages. This class runs on the UI thread and must not do any network I/O, database updates, or
 * any tasks that might take a long time to complete. It also must not start a background thread
 * because that may be killed as soon as {@link #onReceive(Context, Intent)} returns.
 * 
 * You should modify and obfuscate this code before using it.
 */
public class BillingReceiver extends BroadcastReceiver {
    private static final String TAG = "BillingReceiver";

    /**
     * This is the entry point for all asynchronous messages sent from Android Market to the
     * application. This method forwards the messages on to the {@link BillingService}, which
     * handles the communication back to Android Market. The {@link BillingService} also reports
     * state changes back to the application through the {@link ResponseHandler}.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Consts.ACTION_PURCHASE_STATE_CHANGED.equals(action)) {
            String signedData = intent.getStringExtra(Consts.INAPP_SIGNED_DATA);
            String signature = intent.getStringExtra(Consts.INAPP_SIGNATURE);
            purchaseStateChanged(context, signedData, signature);
        } else if (Consts.ACTION_NOTIFY.equals(action)) {
            String notifyId = intent.getStringExtra(Consts.NOTIFICATION_ID);
            if (Consts.DEBUG) {
                Log.i(TAG, "notifyId: " + notifyId);
            }
            notify(context, notifyId);
        } else if (Consts.ACTION_RESPONSE_CODE.equals(action)) {
            long requestId = intent.getLongExtra(Consts.INAPP_REQUEST_ID, -1);
            int responseCodeIndex = intent.getIntExtra(Consts.INAPP_RESPONSE_CODE,
                    ResponseCode.RESULT_ERROR.ordinal());
            checkResponseCode(context, requestId, responseCodeIndex);
        } else {
            Log.w(TAG, "unexpected action: " + action);
        }
    }

    /**
     * This is called when Android Market sends information about a purchase state change. The
     * signedData parameter is a plaintext JSON string that is signed by the server with the
     * developer's private key. The signature for the signed data is passed in the signature
     * parameter.
     * 
     * @param context
     *            the context
     * @param signedData
     *            the (unencrypted) JSON string
     * @param signature
     *            the signature for the signedData
     */
    private void purchaseStateChanged(Context context, String signedData, String signature) {
        Intent intent = new Intent(Consts.ACTION_PURCHASE_STATE_CHANGED);
        intent.setClass(context, BillingService.class);
        intent.putExtra(Consts.INAPP_SIGNED_DATA, signedData);
        intent.putExtra(Consts.INAPP_SIGNATURE, signature);
        context.startService(intent);
    }

    /**
     * This is called when Android Market sends a "notify" message indicating that transaction
     * information is available. The request includes a nonce (random number used once) that we
     * generate and Android Market signs and sends back to us with the purchase state and other
     * transaction details. This BroadcastReceiver cannot bind to the MarketBillingService directly
     * so it starts the {@link BillingService}, which does the actual work of sending the message.
     * 
     * @param context
     *            the context
     * @param notifyId
     *            the notification ID
     */
    private void notify(Context context, String notifyId) {
        Intent intent = new Intent(Consts.ACTION_GET_PURCHASE_INFORMATION);
        intent.setClass(context, BillingService.class);
        intent.putExtra(Consts.NOTIFICATION_ID, notifyId);
        context.startService(intent);
    }

    /**
     * This is called when Android Market sends a server response code. The BillingService can then
     * report the status of the response if desired.
     * 
     * @param context
     *            the context
     * @param requestId
     *            the request ID that corresponds to a previous request
     * @param responseCodeIndex
     *            the ResponseCode ordinal value for the request
     */
    private void checkResponseCode(Context context, long requestId, int responseCodeIndex) {
        Intent intent = new Intent(Consts.ACTION_RESPONSE_CODE);
        intent.setClass(context, BillingService.class);
        intent.putExtra(Consts.INAPP_REQUEST_ID, requestId);
        intent.putExtra(Consts.INAPP_RESPONSE_CODE, responseCodeIndex);
        context.startService(intent);
    }
}
