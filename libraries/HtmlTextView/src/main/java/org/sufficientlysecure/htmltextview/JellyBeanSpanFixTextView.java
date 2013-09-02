/*
 * Copyright (C) 2013 Dominik Schürmann <dominik@dominikschuermann.de>
 * Copyright (C) 2012 Pierre-Yves Ricau <py.ricau@gmail.com>
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

package org.sufficientlysecure.htmltextview;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.TextView;

/**
 * <p>
 * A {@link android.widget.TextView} that insert spaces around its text spans where needed to prevent
 * {@link IndexOutOfBoundsException} in {@link #onMeasure(int, int)} on Jelly Bean.
 * <p>
 * When {@link #onMeasure(int, int)} throws an exception, we try to fix the text by adding spaces
 * around spans, until it works again. We then try removing some of the added spans, to minimize the
 * insertions.
 * <p>
 * The fix is time consuming (a few ms, it depends on the size of your text), but it should only
 * happen once per text change.
 * <p>
 * See http://code.google.com/p/android/issues/detail?id=35466
 * 
 */
public class JellyBeanSpanFixTextView extends TextView {

    private static class FixingResult {
        public final boolean fixed;
        public final List<Object> spansWithSpacesBefore;
        public final List<Object> spansWithSpacesAfter;

        public static FixingResult fixed(List<Object> spansWithSpacesBefore,
                List<Object> spansWithSpacesAfter) {
            return new FixingResult(true, spansWithSpacesBefore, spansWithSpacesAfter);
        }

        public static FixingResult notFixed() {
            return new FixingResult(false, null, null);
        }

        private FixingResult(boolean fixed, List<Object> spansWithSpacesBefore,
                List<Object> spansWithSpacesAfter) {
            this.fixed = fixed;
            this.spansWithSpacesBefore = spansWithSpacesBefore;
            this.spansWithSpacesAfter = spansWithSpacesAfter;
        }
    }

    public JellyBeanSpanFixTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public JellyBeanSpanFixTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public JellyBeanSpanFixTextView(Context context) {
        super(context);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        try {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } catch (IndexOutOfBoundsException e) {
            fixOnMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    /**
     * If possible, fixes the Spanned text by adding spaces around spans when needed.
     */
    private void fixOnMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        CharSequence text = getText();
        if (text instanceof Spanned) {
            SpannableStringBuilder builder = new SpannableStringBuilder(text);
            fixSpannedWithSpaces(builder, widthMeasureSpec, heightMeasureSpec);
        } else {
            if (BuildConfig.DEBUG) {
                Log.d(HtmlTextView.TAG, "The text isn't a Spanned");
            }
            fallbackToString(widthMeasureSpec, heightMeasureSpec);
        }
    }

    /**
     * Add spaces around spans until the text is fixed, and then removes the unneeded spaces
     */
    private void fixSpannedWithSpaces(SpannableStringBuilder builder, int widthMeasureSpec,
            int heightMeasureSpec) {
        long startFix = System.currentTimeMillis();

        FixingResult result = addSpacesAroundSpansUntilFixed(builder, widthMeasureSpec,
                heightMeasureSpec);

        if (result.fixed) {
            removeUnneededSpaces(widthMeasureSpec, heightMeasureSpec, builder, result);
        } else {
            fallbackToString(widthMeasureSpec, heightMeasureSpec);
        }

        if (BuildConfig.DEBUG) {
            long fixDuration = System.currentTimeMillis() - startFix;
            Log.d(HtmlTextView.TAG, "fixSpannedWithSpaces() duration in ms: " + fixDuration);
        }
    }

    private FixingResult addSpacesAroundSpansUntilFixed(SpannableStringBuilder builder,
            int widthMeasureSpec, int heightMeasureSpec) {

        Object[] spans = builder.getSpans(0, builder.length(), Object.class);
        List<Object> spansWithSpacesBefore = new ArrayList<Object>(spans.length);
        List<Object> spansWithSpacesAfter = new ArrayList<Object>(spans.length);

        for (Object span : spans) {
            int spanStart = builder.getSpanStart(span);
            if (isNotSpace(builder, spanStart - 1)) {
                builder.insert(spanStart, " ");
                spansWithSpacesBefore.add(span);
            }

            int spanEnd = builder.getSpanEnd(span);
            if (isNotSpace(builder, spanEnd)) {
                builder.insert(spanEnd, " ");
                spansWithSpacesAfter.add(span);
            }

            try {
                setTextAndMeasure(builder, widthMeasureSpec, heightMeasureSpec);
                return FixingResult.fixed(spansWithSpacesBefore, spansWithSpacesAfter);
            } catch (IndexOutOfBoundsException notFixed) {
            }
        }
        if (BuildConfig.DEBUG) {
            Log.d(HtmlTextView.TAG, "Could not fix the Spanned by adding spaces around spans");
        }
        return FixingResult.notFixed();
    }

    private boolean isNotSpace(CharSequence text, int where) {
        if (where < 0)
            return true;
        return text.charAt(where) != ' ';
    }

    private void setTextAndMeasure(CharSequence text, int widthMeasureSpec, int heightMeasureSpec) {
        setText(text);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void removeUnneededSpaces(int widthMeasureSpec, int heightMeasureSpec,
            SpannableStringBuilder builder, FixingResult result) {

        for (Object span : result.spansWithSpacesAfter) {
            int spanEnd = builder.getSpanEnd(span);
            builder.delete(spanEnd, spanEnd + 1);
            try {
                setTextAndMeasure(builder, widthMeasureSpec, heightMeasureSpec);
            } catch (IndexOutOfBoundsException ignored) {
                builder.insert(spanEnd, " ");
            }
        }

        boolean needReset = true;
        for (Object span : result.spansWithSpacesBefore) {
            int spanStart = builder.getSpanStart(span);
            builder.delete(spanStart - 1, spanStart);
            try {
                setTextAndMeasure(builder, widthMeasureSpec, heightMeasureSpec);
                needReset = false;
            } catch (IndexOutOfBoundsException ignored) {
                needReset = true;
                int newSpanStart = spanStart - 1;
                builder.insert(newSpanStart, " ");
            }
        }

        if (needReset) {
            setText(builder);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    private void fallbackToString(int widthMeasureSpec, int heightMeasureSpec) {
        if (BuildConfig.DEBUG) {
            Log.d(HtmlTextView.TAG, "Fallback to unspanned text");
        }
        String fallbackText = getText().toString();
        setTextAndMeasure(fallbackText, widthMeasureSpec, heightMeasureSpec);
    }

}