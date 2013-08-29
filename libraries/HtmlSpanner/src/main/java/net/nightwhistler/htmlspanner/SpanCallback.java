package net.nightwhistler.htmlspanner;

import android.text.SpannableStringBuilder;

/**
 * Created with IntelliJ IDEA.
 * User: alex
 * Date: 5/6/13
 * Time: 3:13 PM
 * To change this template use File | Settings | File Templates.
 */
public interface SpanCallback {

    void applySpan( HtmlSpanner spanner, SpannableStringBuilder builder );

}


