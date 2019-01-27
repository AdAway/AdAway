/*
 * Copyright (C) 2011-2012 Dominik Schürmann <dominik@dominikschuermann.de>
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

package org.adaway.ui.help;

import org.sufficientlysecure.htmltextview.HtmlTextView;

import android.app.Activity;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

public class HelpFragmentHtml extends Fragment {
    public static final String ARG_HTML_FILE = "htmlFile";
    private Activity mActivity;
    private int htmlFile;

    /**
     * Create a new instance of HelpFragmentHtml, providing "htmlFile" as an argument.
     */
    static HelpFragmentHtml newInstance(int htmlFile) {
        HelpFragmentHtml f = new HelpFragmentHtml();

        // Supply html raw file input as an argument.
        Bundle args = new Bundle();
        args.putInt(ARG_HTML_FILE, htmlFile);
        f.setArguments(args);

        return f;
    }

    /**
     * Workaround for Android Bug. See
     * http://stackoverflow.com/questions/8748064/starting-activity-from
     * -fragment-causes-nullpointerexception
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        setUserVisibleHint(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        htmlFile = getArguments().getInt(ARG_HTML_FILE);

        mActivity = getActivity();

        ScrollView scroller = new ScrollView(mActivity);
        HtmlTextView text = new HtmlTextView(mActivity);

        // padding
        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, mActivity
                .getResources().getDisplayMetrics());
        text.setPadding(padding, padding, padding, 0);

        scroller.addView(text);

        // load html from raw resource (Parsing handled by HtmlTextView library)
        text.setHtml(htmlFile);

        // no flickering when clicking textview for Android < 4
        text.setTextColor(getResources().getColor(android.R.color.secondary_text_dark_nodisable));

        return scroller;
    }
}