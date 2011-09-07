package org.adaway.ui;

import org.adaway.R;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class HelpFAQFragment extends Fragment {
    Activity mActivity;
    TextView mHelpText;

    /**
     * Inflate the layout for this fragment
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.help_fragment, container, false);

        mHelpText = (TextView) view.findViewById(R.id.help_fragment_text);

        String helpText = getString(R.string.help_faq);

        // set text from resources with html markup
        mHelpText.setText(Html.fromHtml(helpText));
        // set scrollable
        mHelpText.setMovementMethod(ScrollingMovementMethod.getInstance());

        return view;
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActivity = getActivity();
    }
}
