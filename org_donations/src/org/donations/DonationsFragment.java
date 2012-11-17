/*
 * Copyright (C) 2011-2012 Dominik Schürmann <dominik@dominikschuermann.de>
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
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebView.HitTestResult;
import android.widget.FrameLayout;

import android.content.DialogInterface;
import android.os.Handler;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

public class DonationsFragment extends Fragment {
    private DonatePurchaseObserver mDonatePurchaseObserver;
    private Handler mHandler;

    private Spinner mGoogleSpinner;
    private TextView mFlattrUrl;

    private BillingService mBillingService;

    private static final int DIALOG_BILLING_NOT_SUPPORTED_ID = 1;
    private static final int DIALOG_THANKS = 2;

    /** An array of product list entries for the products that can be purchased. */
    private static String[] CATALOG;

    private static final String[] CATALOG_DEBUG = new String[] { "android.test.purchased",
            "android.test.canceled", "android.test.refunded", "android.test.item_unavailable" };

    private boolean mGoogleEnabled;

    /**
     * A {@link PurchaseObserver} is used to get callbacks when Android Market sends messages to
     * this application so that we can update the UI.
     */
    private class DonatePurchaseObserver extends PurchaseObserver {
        public DonatePurchaseObserver(Handler handler) {
            super(getActivity(), handler);
        }

        @Override
        public void onBillingSupported(boolean supported) {
            Log.d(DonationsUtils.TAG, "supported: " + supported);
            if (!supported) {
                displayDialog(DIALOG_BILLING_NOT_SUPPORTED_ID);
            }
        }

        @Override
        public void onPurchaseStateChange(PurchaseState purchaseState, String itemId,
                final String orderId, long purchaseTime, String developerPayload) {
            Log.d(DonationsUtils.TAG, "onPurchaseStateChange() itemId: " + itemId + " "
                    + purchaseState);
        }

        @Override
        public void onRequestPurchaseResponse(RequestPurchase request, ResponseCode responseCode) {
            Log.d(DonationsUtils.TAG, request.mProductId + ": " + responseCode);
            if (responseCode == ResponseCode.RESULT_OK) {
                Log.d(DonationsUtils.TAG, "purchase was successfully sent to server");
                displayDialog(DIALOG_THANKS);
            } else if (responseCode == ResponseCode.RESULT_USER_CANCELED) {
                Log.d(DonationsUtils.TAG, "user canceled purchase");
            } else {
                Log.d(DonationsUtils.TAG, "purchase failed");
            }
        }

        @Override
        public void onRestoreTransactionsResponse(RestoreTransactions request,
                ResponseCode responseCode) {
            if (responseCode == ResponseCode.RESULT_OK) {
                Log.d(DonationsUtils.TAG, "completed RestoreTransactions request");
            } else {
                Log.d(DonationsUtils.TAG, "RestoreTransactions error: " + responseCode);
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.donations__fragment, container, false);

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        /* Flattr */
        if (DonationsUtils.getResourceBoolean(getActivity(), "donations__flattr_enabled")) {
            // inflate flattr view into stub
            ViewStub flattrViewStub = (ViewStub) getActivity().findViewById(
                    R.id.donations__flattr_stub);
            flattrViewStub.inflate();

            buildFlattrView();
        }

        /* Google */
        mGoogleEnabled = DonationsUtils.getResourceBoolean(getActivity(),
                "donations__google_enabled");
        if (mGoogleEnabled) {
            // inflate google view into stub
            ViewStub googleViewStub = (ViewStub) getActivity().findViewById(
                    R.id.donations__google_stub);
            googleViewStub.inflate();

            // get catalog from xml config
            CATALOG = DonationsUtils.getResourceStringArray(getActivity(),
                    "donations__google_catalog");

            // choose donation amount
            mGoogleSpinner = (Spinner) getActivity().findViewById(
                    R.id.donations__google_android_market_spinner);
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getActivity(),
                    R.array.donations__google_android_market_promt_array,
                    android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            mGoogleSpinner.setAdapter(adapter);

            mHandler = new Handler();
            mDonatePurchaseObserver = new DonatePurchaseObserver(mHandler);
            mBillingService = new BillingService();
            mBillingService.setContext(getActivity());

            Button btGoogle = (Button) getActivity().findViewById(
                    R.id.donations__google_android_market_donate_button);
            btGoogle.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    donateGoogleOnClick(v);
                }
            });
        }

        /* PayPal */
        if (DonationsUtils.getResourceBoolean(getActivity(), "donations__paypal_enabled")) {
            // inflate paypal view into stub
            ViewStub paypalViewStub = (ViewStub) getActivity().findViewById(
                    R.id.donations__paypal_stub);
            paypalViewStub.inflate();

            Button btPayPal = (Button) getActivity().findViewById(
                    R.id.donations__paypal_donate_button);
            btPayPal.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    donatePayPalOnClick(v);
                }
            });
        }
    }

    /**
     * Donate button executes donations based on selection in spinner
     * 
     * @param view
     */
    public void donateGoogleOnClick(View view) {
        final int index;
        index = mGoogleSpinner.getSelectedItemPosition();
        Log.d(DonationsUtils.TAG, "selected item in spinner: " + index);

        if (!Consts.DEBUG) {
            if (!mBillingService.requestPurchase(CATALOG[index], null)) {
                displayDialog(DIALOG_BILLING_NOT_SUPPORTED_ID);
            }
        } else {
            // when debugging, choose android.test.x item
            if (!mBillingService.requestPurchase(CATALOG_DEBUG[0], null)) {
                displayDialog(DIALOG_BILLING_NOT_SUPPORTED_ID);
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

        uriBuilder.appendQueryParameter("business",
                DonationsUtils.getResourceString(getActivity(), "donations__paypal_user"));
        uriBuilder.appendQueryParameter("lc", "US");
        uriBuilder.appendQueryParameter("item_name",
                DonationsUtils.getResourceString(getActivity(), "donations__paypal_item_name"));
        uriBuilder.appendQueryParameter("no_note", "1");
        // uriBuilder.appendQueryParameter("no_note", "0");
        // uriBuilder.appendQueryParameter("cn", "Note to the developer");
        uriBuilder.appendQueryParameter("no_shipping", "1");
        uriBuilder.appendQueryParameter("currency_code",
                DonationsUtils.getResourceString(getActivity(), "donations__paypal_currency_code"));
        // uriBuilder.appendQueryParameter("bn", "PP-DonationsBF:btn_donate_LG.gif:NonHosted");
        Uri payPalUri = uriBuilder.build();

        if (DonationsUtils.DEBUG) {
            Log.d(DonationsUtils.TAG, "Opening the browser with the url: " + payPalUri.toString());
        }

        // Start your favorite browser
        Intent viewIntent = new Intent(Intent.ACTION_VIEW, payPalUri);
        startActivity(viewIntent);
    }

    /**
     * Called when this activity becomes visible.
     */
    @Override
    public void onStart() {
        super.onStart();

        if (mGoogleEnabled) {
            ResponseHandler.register(mDonatePurchaseObserver);
        }
    }

    /**
     * Called when this activity is no longer visible.
     */
    @Override
    public void onStop() {
        super.onStop();

        if (mGoogleEnabled) {
            ResponseHandler.unregister(mDonatePurchaseObserver);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mGoogleEnabled) {
            mBillingService.unbind();
        }
    }

    private void displayDialog(int id) {
        int icon = -1;
        int title = -1;
        int message = -1;

        switch (id) {
        case DIALOG_BILLING_NOT_SUPPORTED_ID:
            icon = android.R.drawable.ic_dialog_alert;
            title = R.string.donations__google_android_market_not_supported_title;
            message = R.string.donations__google_android_market_not_supported;
        case DIALOG_THANKS:
            icon = android.R.drawable.ic_dialog_info;
            title = R.string.donations__thanks_dialog_title;
            message = R.string.donations__thanks_dialog;
        default:
        }

        AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity());
        dialog.setIcon(icon);
        dialog.setTitle(title);
        dialog.setMessage(message);
        dialog.setCancelable(true);
        dialog.setNeutralButton(R.string.donations__button_close,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        dialog.show();
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

        mFlattrWebview = (WebView) getActivity().findViewById(R.id.donations__flattr_webview);
        mLoadingFrame = (FrameLayout) getActivity().findViewById(R.id.donations__loading_frame);

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

        // get flattr values from xml config
        String projectUrl = DonationsUtils.getResourceString(getActivity(),
                "donations__flattr_project_url");
        String flattrUrl = DonationsUtils.getResourceString(getActivity(), "donations__flattr_url");

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
        mFlattrUrl = (TextView) getActivity().findViewById(R.id.donations__flattr_url);
        mFlattrUrl.setText(flattrScheme + flattrUrl);

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
