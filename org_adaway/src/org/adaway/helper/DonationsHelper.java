///*
// * Copyright (C) 2011 Dominik Schürmann <dominik@dominikschuermann.de>
// *
// * This file is part of AdAway.
// * 
// * AdAway is free software: you can redistribute it and/or modify
// * it under the terms of the GNU General Public License as published by
// * the Free Software Foundation, either version 3 of the License, or
// * (at your option) any later version.
// *
// * AdAway is distributed in the hope that it will be useful,
// * but WITHOUT ANY WARRANTY; without even the implied warranty of
// * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// * GNU General Public License for more details.
// *
// * You should have received a copy of the GNU General Public License
// * along with AdAway.  If not, see <http://www.gnu.org/licenses/>.
// *
// */
//
//package org.adaway.helper;
//
//import org.adaway.R;
//
//import android.app.Activity;
//import android.app.AlertDialog;
//import android.content.DialogInterface;
//import android.content.Intent;
//import android.net.Uri;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.webkit.WebView;
//import android.webkit.WebViewClient;
//import android.widget.FrameLayout;
//
//public class DonationsHelper {
//
//    /**
//     * Donations Dialog of AdAway
//     */
//    public static void showDonationsDialog(Activity activity) {
//
//        builder.setView(dialogView);
//
//        builder.setIcon(R.drawable.ic_dialog_love);
//        builder.setNeutralButton(activity.getString(R.string.button_close),
//                new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int id) {
//                        dialog.dismiss();
//                    }
//                });
//        
//        /*
//         * GOOGLE ANDROID MARKET
//         */
//        
//        
//        AlertDialog question = builder.create();
//        question.show();
//    }
//
//}
