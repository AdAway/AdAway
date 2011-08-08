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

package org.adaway.utils;

import org.adaway.R;

import android.webkit.WebView;
import android.content.Context;
import android.util.AttributeSet;

/**
 * 
 * Partly taken from
 * http://www.dafer45.com/android/for_developers/flattr_view_example_application_how_to.html
 * http://www.dafer45.com/android/for_developers/including_a_flattr_button_in_an_application.html
 */
public class FlattrView extends WebView {
    public FlattrView(Context context, AttributeSet attrs) {
        super(context, attrs);
        String flattrURL = "http://code.google.com/p/ad-away";
        String donations_description = context.getString(R.string.donations_description);
        
        String htmlStart = "<html> <head>";
        String flattrJavascript = "<script type=\"text/javascript\"> /* <![CDATA[ */    (function() {        var s = document.createElement('script'), t = document.getElementsByTagName('script')[0];        s.type = 'text/javascript';        s.async = true;        s.src = 'http://api.flattr.com/js/0.6/load.js?mode=auto';        t.parentNode.insertBefore(s, t);    })();/* ]]> */</script>";
        String htmlMiddle = "</head> <body> <table> <tr> <td>";
        String flattrHtml = "<a class=\"FlattrButton\" style=\"display:none;\" href=\""
                + flattrURL
                + "\" target=\"_blank\"></a> <noscript><a href=\"http://flattr.com/thing/369138/AdAway-Ad-blocker-for-Android\" target=\"_blank\"> <img src=\"http://api.flattr.com/button/flattr-badge-large.png\" alt=\"Flattr this\" title=\"Flattr this\" border=\"0\" /></a></noscript>";

        String htmlEnd = "</td> <td>" + donations_description +"</td> </tr> </table> </body> </html>";
        // String flattrCode = "<html>" + "<head>" + "<script type=\"text/javascript\">"
        // + "/* <![CDATA[ */" + "(function() {" + "var s = document.createElement('script'),"
        // + " t = document.getElementsByTagName('script')[0];"
        // + "s.type = 'text/javascript';" + "s.async = true;"
        // + "s.src = 'http://api.flattr.com/js/0.5.0/load.js?mode=auto';"
        // + "t.parentNode.insertBefore(s, t);" + "})();" + "/* ]]> */" + "</script>"
        // + "</head>" + "<body>" + "<table>" + "<tr>" + "<td>"
        // + "<a class=\"FlattrButton\" style=\"display:none;\"" + "href=\"" + flattrURL
        // + "\"></a>" + "</td>" + "<td>" + "Do you find this application useful?"
        // + " Support it's development by flattring it!" + "</td>" + "</tr>" + "</table>"
        // + "</body>" + "</html>";

        String flattrCode = htmlStart+ flattrJavascript + htmlMiddle + flattrHtml + htmlEnd;
        getSettings().setJavaScriptEnabled(true);
        loadData(flattrCode, "text/html", "utf-8");
    }
}