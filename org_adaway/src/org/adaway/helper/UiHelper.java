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

package org.adaway.helper;

import org.adaway.R;
import org.adaway.util.Constants;
import org.adaway.util.Log;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

public class UiHelper {

    /**
     * Donations Dialog of AdAway
     */
    public static void showDonationsDialog(Activity activity) {
        final FrameLayout mLoadingFrame;
        final WebView mFlattrWebview;

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.donations_title);

        // build view from layout
        LayoutInflater factory = LayoutInflater.from(activity);
        final View dialogView = factory.inflate(R.layout.donation_dialog, null);

        mFlattrWebview = (WebView) dialogView.findViewById(R.id.flattr_webview);
        mLoadingFrame = (FrameLayout) dialogView.findViewById(R.id.loading_frame);

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
        String projectUrl = activity.getString(R.string.about_url);
        String flattrUrl = activity.getString(R.string.donations_url);

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
        mFlattrWebview.setBackgroundColor(0x00000000);

        builder.setView(dialogView);

        builder.setIcon(R.drawable.ic_dialog_love);
        builder.setNeutralButton(activity.getString(R.string.button_close),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });
        AlertDialog question = builder.create();
        question.show();
    }

    /**
     * About Dialog of AdAway
     */
    public static void showAboutDialog(Activity activity) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.about_title);

        // build view from layout
        LayoutInflater factory = LayoutInflater.from(activity);
        final View dialogView = factory.inflate(R.layout.about_dialog, null);

        TextView versionText = (TextView) dialogView.findViewById(R.id.about_version);
        versionText
                .setText(activity.getString(R.string.about_version) + " " + getVersion(activity));

        builder.setView(dialogView);

        builder.setIcon(android.R.drawable.ic_dialog_info);
        builder.setNeutralButton(activity.getString(R.string.button_close),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });
        AlertDialog question = builder.create();
        question.show();
    }

    /**
     * Get the current package version.
     * 
     * @return The current version.
     */
    private static String getVersion(Activity activity) {
        String result = "";
        try {
            PackageManager manager = activity.getPackageManager();
            PackageInfo info = manager.getPackageInfo(activity.getPackageName(), 0);

            result = String.format("%s (%s)", info.versionName, info.versionCode);
        } catch (NameNotFoundException e) {
            Log.w(Constants.TAG, "Unable to get application version: " + e.getMessage());
            result = "Unable to get application version.";
        }

        return result;
    }

}
