/*
 * Copyright (C) 2011 Dominik Schürmann <dominik@dominikschuermann.de>
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

package org.donations;

import org.donations.google.BillingService;
import org.donations.google.Consts;
import org.donations.google.PurchaseObserver;
import org.donations.google.ResponseHandler;
import org.donations.google.BillingService.RequestPurchase;
import org.donations.google.BillingService.RestoreTransactions;
import org.donations.google.Consts.PurchaseState;
import org.donations.google.Consts.ResponseCode;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebView.HitTestResult;
import android.widget.FrameLayout;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

public class DonationsActivity extends Activity {
    private DonatePurchaseObserver mDonatePurchaseObserver;
    private Handler mHandler;

    private Spinner mGoogleAndroidMarketSpinner;

    private TextView mFlattrUrl;

    private BillingService mBillingService;

    private static final int DIALOG_BILLING_NOT_SUPPORTED_ID = 1;

    /** An array of product list entries for the products that can be purchased. */
    private static final String[] CATALOG = DonationsConfiguration.GOOGLE_CATALOG;

    private static final String[] CATALOG_DEBUG = new String[] { "android.test.purchased",
            "android.test.canceled", "android.test.refunded", "android.test.item_unavailable" };

    /**
     * A {@link PurchaseObserver} is used to get callbacks when Android Market sends messages to
     * this application so that we can update the UI.
     */
    private class DonatePurchaseObserver extends PurchaseObserver {
        public DonatePurchaseObserver(Handler handler) {
            super(DonationsActivity.this, handler);
        }

        @Override
        public void onBillingSupported(boolean supported) {
            Log.d(DonationsConfiguration.TAG, "supported: " + supported);
            if (!supported) {
                showDialog(DIALOG_BILLING_NOT_SUPPORTED_ID);
            }
        }

        @Override
        public void onPurchaseStateChange(PurchaseState purchaseState, String itemId,
                final String orderId, long purchaseTime, String developerPayload) {
            Log.d(DonationsConfiguration.TAG, "onPurchaseStateChange() itemId: " + itemId + " "
                    + purchaseState);
        }

        @Override
        public void onRequestPurchaseResponse(RequestPurchase request, ResponseCode responseCode) {
            Log.d(DonationsConfiguration.TAG, request.mProductId + ": " + responseCode);
            if (responseCode == ResponseCode.RESULT_OK) {
                Log.d(DonationsConfiguration.TAG, "purchase was successfully sent to server");
                AlertDialog.Builder dialog = new AlertDialog.Builder(DonationsActivity.this);
                dialog.setIcon(android.R.drawable.ic_dialog_info);
                dialog.setTitle(R.string.donations__thanks_dialog_title);
                dialog.setMessage(R.string.donations__thanks_dialog);
                dialog.setCancelable(true);
                dialog.setNeutralButton(R.string.donations__button_close,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                dialog.show();
            } else if (responseCode == ResponseCode.RESULT_USER_CANCELED) {
                Log.d(DonationsConfiguration.TAG, "user canceled purchase");
            } else {
                Log.d(DonationsConfiguration.TAG, "purchase failed");
            }
        }

        @Override
        public void onRestoreTransactionsResponse(RestoreTransactions request,
                ResponseCode responseCode) {
            if (responseCode == ResponseCode.RESULT_OK) {
                Log.d(DonationsConfiguration.TAG, "completed RestoreTransactions request");
            } else {
                Log.d(DonationsConfiguration.TAG, "RestoreTransactions error: " + responseCode);
            }
        }
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.donations__activity);

        // build everything for flattr
        buildFlattrView();

        // choose donation amount
        mGoogleAndroidMarketSpinner = (Spinner) findViewById(R.id.donations__google_android_market_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.donations__google_android_market_promt_array,
                android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mGoogleAndroidMarketSpinner.setAdapter(adapter);

        mHandler = new Handler();
        mDonatePurchaseObserver = new DonatePurchaseObserver(mHandler);
        mBillingService = new BillingService();
        mBillingService.setContext(this);
    }

    /**
     * Donate button executes donations based on selection in spinner
     * 
     * @param view
     */
    public void donateGoogleOnClick(View view) {
        final int index;
        index = mGoogleAndroidMarketSpinner.getSelectedItemPosition();
        Log.d(DonationsConfiguration.TAG, "selected item in spinner: " + index);

        if (!Consts.DEBUG) {
            if (!mBillingService.requestPurchase(CATALOG[index], null)) {
                showDialog(DIALOG_BILLING_NOT_SUPPORTED_ID);
            }
        } else {
            // when debugging, choose android.test.x item
            if (!mBillingService.requestPurchase(CATALOG_DEBUG[0], null)) {
                showDialog(DIALOG_BILLING_NOT_SUPPORTED_ID);
            }
        }
    }

    /**
     * Donate button with PayPal by opening browser with defined URL For possible parameters see:
     * https://cms.paypal.com/us/cgi-bin/?cmd=_render-content&content_ID=developer/
     * e_howto_html_Appx_websitestandard_htmlvariables
     * 
     * @param view
     */
    public void donatePayPalOnClick(View view) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.scheme("https").authority("www.paypal.com").path("cgi-bin/webscr");
        uriBuilder.appendQueryParameter("cmd", "_donations");
        uriBuilder.appendQueryParameter("business", DonationsConfiguration.PAYPAL_USER);
        uriBuilder.appendQueryParameter("lc", "US");
        uriBuilder.appendQueryParameter("item_name", DonationsConfiguration.PAYPAL_ITEM_NAME);
        uriBuilder.appendQueryParameter("no_note", "1");
        // uriBuilder.appendQueryParameter("no_note", "0");
        // uriBuilder.appendQueryParameter("cn", "Note to the developer");
        uriBuilder.appendQueryParameter("no_shipping", "1");
        uriBuilder.appendQueryParameter("currency_code",
                DonationsConfiguration.PAYPAL_CURRENCY_CODE);
        // uriBuilder.appendQueryParameter("bn", "PP-DonationsBF:btn_donate_LG.gif:NonHosted");
        Uri payPalUri = uriBuilder.build();

        if (DonationsConfiguration.DEBUG) {
            Log.d(DonationsConfiguration.TAG,
                    "Opening the browser with the url: " + payPalUri.toString());
        }

        // Start your favorite browser
        Intent viewIntent = new Intent(Intent.ACTION_VIEW, payPalUri);
        startActivity(viewIntent);
    }

    /**
     * Called when this activity becomes visible.
     */
    @Override
    protected void onStart() {
        super.onStart();
        ResponseHandler.register(mDonatePurchaseObserver);
    }

    /**
     * Called when this activity is no longer visible.
     */
    @Override
    protected void onStop() {
        super.onStop();
        ResponseHandler.unregister(mDonatePurchaseObserver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBillingService.unbind();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case DIALOG_BILLING_NOT_SUPPORTED_ID:
            return createDialog(
                    getString(R.string.donations__google_android_market_not_supported_title),
                    getString(R.string.donations__google_android_market_not_supported));
        default:
            return null;
        }
    }

    /**
     * Build dialog based on strings
     */
    private Dialog createDialog(String string, String string2) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(string)
                .setIcon(android.R.drawable.stat_sys_warning)
                .setMessage(string2)
                .setCancelable(false)
                .setPositiveButton(R.string.donations__button_close,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
        return builder.create();
    }

    /**
     * Build view for Flattr. see Flattr API for more information:
     * http://developers.flattr.net/button/
     */
    @SuppressLint("SetJavaScriptEnabled")
    @TargetApi(11)
    private void buildFlattrView() {
        final FrameLayout mLoadingFrame;
        final WebView mFlattrWebview;

        mFlattrWebview = (WebView) findViewById(R.id.donations__flattr_webview);
        mLoadingFrame = (FrameLayout) findViewById(R.id.donations__loading_frame);

        // disable hardware acceleration for this webview to get transparent background working
        if (Build.VERSION.SDK_INT >= 11) {
            mFlattrWebview.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        // define own webview client to override loading behaviour
        mFlattrWebview.setWebViewClient(new WebViewClient() {
            /**
             * Open all links in browser, not in webview
             */
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String urlNewString) {
                view.getContext().startActivity(
                        new Intent(Intent.ACTION_VIEW, Uri.parse(urlNewString)));

                return false;
            }

            /**
             * Links in the flattr iframe should load in the browser not in the iframe itself,
             * http:/
             * /stackoverflow.com/questions/5641626/how-to-get-webview-iframe-link-to-launch-the
             * -browser
             */
            @Override
            public void onLoadResource(WebView view, String url) {
                if (url.contains("flattr")) {
                    HitTestResult result = view.getHitTestResult();
                    if (result != null && result.getType() > 0) {
                        view.getContext().startActivity(
                                new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                        view.stopLoading();
                    }
                }
            }

            /**
             * After loading is done, remove frame with progress circle
             */
            @Override
            public void onPageFinished(WebView view, String url) {
                // remove loading frame, show webview
                if (mLoadingFrame.getVisibility() == View.VISIBLE) {
                    mLoadingFrame.setVisibility(View.GONE);
                    mFlattrWebview.setVisibility(View.VISIBLE);
                }
            }
        });

        String projectUrl = DonationsConfiguration.FLATTR_PROJECT_URL;
        String flattrUrl = DonationsConfiguration.FLATTR_URL;

        // make text white and background transparent
        String htmlStart = "<html> <head><style type='text/css'>*{color: #FFFFFF; background-color: transparent;}</style>";

        // https is not working in android 2.1 and 2.2
        String flattrScheme;
        if (Build.VERSION.SDK_INT >= 9) {
            flattrScheme = "https://";
        } else {
            flattrScheme = "http://";
        }

        // set url of flattr link
        mFlattrUrl = (TextView) findViewById(R.id.donations__flattr_url);
        mFlattrUrl.setText(flattrScheme + DonationsConfiguration.FLATTR_URL);

        String flattrJavascript = "<script type='text/javascript'>"
                + "/* <![CDATA[ */"
                + "(function() {"
                + "var s = document.createElement('script'), t = document.getElementsByTagName('script')[0];"
                + "s.type = 'text/javascript';" + "s.async = true;" + "s.src = '" + flattrScheme
                + "api.flattr.com/js/0.6/load.js?mode=auto';" + "t.parentNode.insertBefore(s, t);"
                + "})();" + "/* ]]> */" + "</script>";
        String htmlMiddle = "</head> <body> <div align='center'>";
        String flattrHtml = "<a class='FlattrButton' style='display:none;' href='"
                + projectUrl
                + "' target='_blank'></a> <noscript><a href='"
                + flattrScheme
                + flattrUrl
                + "' target='_blank'> <img src='"
                + flattrScheme
                + "api.flattr.com/button/flattr-badge-large.png' alt='Flattr this' title='Flattr this' border='0' /></a></noscript>";
        String htmlEnd = "</div> </body> </html>";

        String flattrCode = htmlStart + flattrJavascript + htmlMiddle + flattrHtml + htmlEnd;

        mFlattrWebview.getSettings().setJavaScriptEnabled(true);

        mFlattrWebview.loadData(flattrCode, "text/html", "utf-8");

        // make background of webview transparent
        // has to be called AFTER loadData
        // http://stackoverflow.com/questions/5003156/android-webview-style-background-colortransparent-ignored-on-android-2-2
        mFlattrWebview.setBackgroundColor(0x00000000);
    }
}
