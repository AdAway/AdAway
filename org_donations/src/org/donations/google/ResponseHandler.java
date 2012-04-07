/*
 * Copyright (C) 2011 The Android Open Source Project
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

import org.donations.google.BillingService.RequestPurchase;
import org.donations.google.BillingService.RestoreTransactions;
import org.donations.google.Consts.PurchaseState;
import org.donations.google.Consts.ResponseCode;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * This class contains the methods that handle responses from Android Market. The implementation of
 * these methods is specific to a particular application. The methods in this example update the
 * database and, if the main application has registered a {@llink PurchaseObserver}, will also
 * update the UI. An application might also want to forward some responses on to its own server, and
 * that could be done here (in a background thread) but this example does not do that.
 * 
 * You should modify and obfuscate this code before using it.
 */
public class ResponseHandler {
    private static final String TAG = "ResponseHandler";

    /**
     * This is a static instance of {@link PurchaseObserver} that the application creates and
     * registers with this class. The PurchaseObserver is used for updating the UI if the UI is
     * visible.
     */
    private static PurchaseObserver sPurchaseObserver;

    /**
     * Registers an observer that updates the UI.
     * 
     * @param observer
     *            the observer to register
     */
    public static synchronized void register(PurchaseObserver observer) {
        sPurchaseObserver = observer;
    }

    /**
     * Unregisters a previously registered observer.
     * 
     * @param observer
     *            the previously registered observer.
     */
    public static synchronized void unregister(PurchaseObserver observer) {
        sPurchaseObserver = null;
    }

    /**
     * Notifies the application of the availability of the MarketBillingService. This method is
     * called in response to the application calling {@link BillingService#checkBillingSupported()}.
     * 
     * @param supported
     *            true if in-app billing is supported.
     */
    public static void checkBillingSupportedResponse(boolean supported) {
        if (sPurchaseObserver != null) {
            sPurchaseObserver.onBillingSupported(supported);
        }
    }

    /**
     * Starts a new activity for the user to buy an item for sale. This method forwards the intent
     * on to the PurchaseObserver (if it exists) because we need to start the activity on the
     * activity stack of the application.
     * 
     * @param pendingIntent
     *            a PendingIntent that we received from Android Market that will create the new buy
     *            page activity
     * @param intent
     *            an intent containing a request id in an extra field that will be passed to the buy
     *            page activity when it is created
     */
    public static void buyPageIntentResponse(PendingIntent pendingIntent, Intent intent) {
        if (sPurchaseObserver == null) {
            if (Consts.DEBUG) {
                Log.d(TAG, "UI is not running");
            }
            return;
        }
        sPurchaseObserver.startBuyPageActivity(pendingIntent, intent);
    }

    /**
     * Notifies the application of purchase state changes. The application can offer an item for
     * sale to the user via {@link BillingService#requestPurchase(String)}. The BillingService calls
     * this method after it gets the response. Another way this method can be called is if the user
     * bought something on another device running this same app. Then Android Market notifies the
     * other devices that the user has purchased an item, in which case the BillingService will also
     * call this method. Finally, this method can be called if the item was refunded.
     * 
     * @param purchaseState
     *            the state of the purchase request (PURCHASED, CANCELED, or REFUNDED)
     * @param productId
     *            a string identifying a product for sale
     * @param orderId
     *            a string identifying the order
     * @param purchaseTime
     *            the time the product was purchased, in milliseconds since the epoch (Jan 1, 1970)
     * @param developerPayload
     *            the developer provided "payload" associated with the order
     */
    public static void purchaseResponse(final Context context, final PurchaseState purchaseState,
            final String productId, final String orderId, final long purchaseTime,
            final String developerPayload) {

        // Update the database with the purchase state. We shouldn't do that
        // from the main thread so we do the work in a background thread.
        // We don't update the UI here. We will update the UI after we update
        // the database because we need to read and update the current quantity
        // first.
        new Thread(new Runnable() {
            public void run() {
                // PurchaseDatabase db = new PurchaseDatabase(context);
                // int quantity = db.updatePurchase(
                // orderId, productId, purchaseState, purchaseTime, developerPayload);
                // db.close();

                // This needs to be synchronized because the UI thread can change the
                // value of sPurchaseObserver.
                synchronized (ResponseHandler.class) {
                    if (sPurchaseObserver != null) {
                        sPurchaseObserver.postPurchaseStateChange(purchaseState, productId,
                                orderId, purchaseTime, developerPayload);
                    }
                }
            }
        }).start();
    }

    /**
     * This is called when we receive a response code from Android Market for a RequestPurchase
     * request that we made. This is used for reporting various errors and also for acknowledging
     * that an order was sent successfully to the server. This is NOT used for any purchase state
     * changes. All purchase state changes are received in the {@link BillingReceiver} and are
     * handled in {@link Security#verifyPurchase(String, String)}.
     * 
     * @param context
     *            the context
     * @param request
     *            the RequestPurchase request for which we received a response code
     * @param responseCode
     *            a response code from Market to indicate the state of the request
     */
    public static void responseCodeReceived(Context context, RequestPurchase request,
            ResponseCode responseCode) {
        if (sPurchaseObserver != null) {
            sPurchaseObserver.onRequestPurchaseResponse(request, responseCode);
        }
    }

    /**
     * This is called when we receive a response code from Android Market for a RestoreTransactions
     * request.
     * 
     * @param context
     *            the context
     * @param request
     *            the RestoreTransactions request for which we received a response code
     * @param responseCode
     *            a response code from Market to indicate the state of the request
     */
    public static void responseCodeReceived(Context context, RestoreTransactions request,
            ResponseCode responseCode) {
        if (sPurchaseObserver != null) {
            sPurchaseObserver.onRestoreTransactionsResponse(request, responseCode);
        }
    }
}
