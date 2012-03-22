/*
 * Copyright (C) 2011 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This file is part of AdAway.
 * 
 * AdAway is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AdAway is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AdAway.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.adaway.ui;

import org.adaway.R;
import org.adaway.google.donations.BillingService;
import org.adaway.google.donations.Consts;
import org.adaway.google.donations.PurchaseObserver;
import org.adaway.google.donations.ResponseHandler;
import org.adaway.google.donations.BillingService.RequestPurchase;
import org.adaway.google.donations.BillingService.RestoreTransactions;
import org.adaway.google.donations.Consts.PurchaseState;
import org.adaway.google.donations.Consts.ResponseCode;
import org.adaway.util.Constants;
import org.adaway.util.Log;

import com.actionbarsherlock.app.SherlockActivity;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

public class DonationsActivity extends SherlockActivity {
    private DonatePurchaseObserver mDonatePurchaseObserver;
    private Handler mHandler;

    private Spinner mGoogleAndroidMarketSpinner;

    private BillingService mBillingService;

    private static final int DIALOG_BILLING_NOT_SUPPORTED_ID = 1;

    /** An array of product list entries for the products that can be purchased. */
    private static final String[] CATALOG = new String[] { "adaway.donation.1",
            "adaway.donation.2", "adaway.donation.3", "adaway.donation.5", "adaway.donation.8",
            "adaway.donation.13" };

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
            Log.d(Constants.TAG, "supported: " + supported);
            if (!supported) {
                showDialog(DIALOG_BILLING_NOT_SUPPORTED_ID);
            }
        }

        @Override
        public void onPurchaseStateChange(PurchaseState purchaseState, String itemId,
                final String orderId, long purchaseTime, String developerPayload) {
            Log.d(Constants.TAG, "onPurchaseStateChange() itemId: " + itemId + " " + purchaseState);
        }

        @Override
        public void onRequestPurchaseResponse(RequestPurchase request, ResponseCode responseCode) {
            Log.d(Constants.TAG, request.mProductId + ": " + responseCode);
            if (responseCode == ResponseCode.RESULT_OK) {
                Log.d(Constants.TAG, "purchase was successfully sent to server");
                AlertDialog.Builder dialog = new AlertDialog.Builder(DonationsActivity.this);
                dialog.setIcon(android.R.drawable.ic_dialog_info);
                dialog.setTitle(R.string.donations_thanks_dialog_title);
                dialog.setMessage(R.string.donations_thanks_dialog);
                dialog.setCancelable(true);
                dialog.setNeutralButton(R.string.button_close,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                dialog.show();
            } else if (responseCode == ResponseCode.RESULT_USER_CANCELED) {
                Log.d(Constants.TAG, "user canceled purchase");
            } else {
                Log.d(Constants.TAG, "purchase failed");
            }
        }

        @Override
        public void onRestoreTransactionsResponse(RestoreTransactions request,
                ResponseCode responseCode) {
            if (responseCode == ResponseCode.RESULT_OK) {
                Log.d(Constants.TAG, "completed RestoreTransactions request");
            } else {
                Log.d(Constants.TAG, "RestoreTransactions error: " + responseCode);
            }
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.donations_activity);

        // build everything for flattr
        buildFlattrView();

        // choose donation amount
        mGoogleAndroidMarketSpinner = (Spinner) findViewById(R.id.donations_google_android_market_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.donations_google_android_market_promt_array,
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
        Log.d(Constants.TAG, "selected item in spinner: " + index);

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
     * Donate button with PayPal by opening browser with defined URL
     * 
     * @param view
     */
    public void donatePayPalOnClick(View view) {
        // Build PayPal Url
        // For parameters see:
        // https://cms.paypal.com/us/cgi-bin/?cmd=_render-content&content_ID=developer/e_howto_html_Appx_websitestandard_htmlvariables
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.scheme("https").authority("www.paypal.com").path("cgi-bin/webscr");
        uriBuilder.appendQueryParameter("cmd", "_donations");
        uriBuilder.appendQueryParameter("business", "dominik@dominikschuermann.de");
        uriBuilder.appendQueryParameter("lc", "US");
        uriBuilder.appendQueryParameter("item_name", "AdAway Donation");
        uriBuilder.appendQueryParameter("no_note", "1");
        // uriBuilder.appendQueryParameter("no_note", "0");
        // uriBuilder.appendQueryParameter("cn", "Note to the developer");
        uriBuilder.appendQueryParameter("no_shipping", "1");
        uriBuilder.appendQueryParameter("currency_code", "EUR");
        // uriBuilder.appendQueryParameter("bn", "PP-DonationsBF:btn_donate_LG.gif:NonHosted");
        Uri payPalUri = uriBuilder.build();

        Log.d(Constants.TAG, "Opening the browser with the url: " + payPalUri.toString());

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
                    getString(R.string.donations_google_android_market_not_supported_title),
                    getString(R.string.donations_google_android_market_not_supported));
        default:
            return null;
        }
    }

    /**
     * Build dialog based on strings
     */
    private Dialog createDialog(String string, String string2) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(string).setIcon(android.R.drawable.stat_sys_warning).setMessage(string2)
                .setCancelable(false)
                .setPositiveButton(R.string.button_close, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        return builder.create();
    }

    /**
     * Build view for flattr
     */
    private void buildFlattrView() {
        final FrameLayout mLoadingFrame;
        final WebView mFlattrWebview;

        mFlattrWebview = (WebView) findViewById(R.id.flattr_webview);
        mLoadingFrame = (FrameLayout) findViewById(R.id.loading_frame);

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
                    if (view.getHitTestResult().getType() > 0) {
                        view.getContext().startActivity(
                                new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                        view.stopLoading();
                    }
                }
            }

            /**
             * When loading is done remove frame with progress circle
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

        /*
         * Partly taken from
         * http://www.dafer45.com/android/for_developers/flattr_view_example_application_how_to.html
         * http
         * ://www.dafer45.com/android/for_developers/including_a_flattr_button_in_an_application.
         * html
         */
        String projectUrl = getString(R.string.about_url);
        String flattrUrl = getString(R.string.donations_flattr_url);

        // make text white and background black
        String htmlStart = "<html> <head><style type=\"text/css\">*{color: #FFFFFF; background-color: transparent;}</style>";

        // see flattr api https://flattr.com/support/integrate/js
        String flattrParameter = "mode=auto"; // &https=1 not working in android 2.1 and 2.2
        String flattrJavascript = "<script type=\"text/javascript\">"
                + "/* <![CDATA[ */"
                + "(function() {"
                + "var s = document.createElement('script'), t = document.getElementsByTagName('script')[0];"
                + "s.type = 'text/javascript';" + "s.async = true;"
                + "s.src = 'http://api.flattr.com/js/0.6/load.js?" + flattrParameter + "';"
                + "t.parentNode.insertBefore(s, t);" + "})();" + "/* ]]> */" + "</script>";
        String htmlMiddle = "</head> <body> <div align=\"center\">";
        String flattrHtml = "<a class=\"FlattrButton\" style=\"display:none;\" href=\""
                + projectUrl
                + "\" target=\"_blank\"></a> <noscript><a href=\""
                + flattrUrl
                + "\" target=\"_blank\"> <img src=\"http://api.flattr.com/button/flattr-badge-large.png\" alt=\"Flattr this\" title=\"Flattr this\" border=\"0\" /></a></noscript>";
        String htmlEnd = "</div> </body> </html>";

        String flattrCode = htmlStart + flattrJavascript + htmlMiddle + flattrHtml + htmlEnd;

        mFlattrWebview.getSettings().setJavaScriptEnabled(true);

        mFlattrWebview.loadData(flattrCode, "text/html", "utf-8");

        // make background of webview transparent
        // has to be called AFTER loadData
        // http://stackoverflow.com/questions/5003156/android-webview-style-background-colortransparent-ignored-on-android-2-2
        mFlattrWebview.setBackgroundColor(0x00000000);

        // disable hardware accleration for this webview to get transparent background working
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            mFlattrWebview.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
    }
}
