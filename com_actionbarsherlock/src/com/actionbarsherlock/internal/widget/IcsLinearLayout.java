package com.actionbarsherlock.internal.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import com.actionbarsherlock.internal.nineoldandroids.widget.NineLinearLayout;

/**
 * A simple extension of a regular linear layout that supports the divider API
 * of Android 4.0+.
 */
public class IcsLinearLayout extends NineLinearLayout {
    private static final int[] LinearLayout = new int[] {
        /* 0 */ android.R.attr.divider,
        /* 1 */ android.R.attr.showDividers,
        /* 2 */ android.R.attr.dividerPadding,
    };
    private static final int LinearLayout_divider = 0;
    private static final int LinearLayout_showDividers = 1;
    private static final int LinearLayout_dividerPadding = 2;

    /**
     * Don't show any dividers.
     */
    public static final int SHOW_DIVIDER_NONE = 0;
    /**
     * Show a divider at the beginning of the group.
     */
    public static final int SHOW_DIVIDER_BEGINNING = 1;
    /**
     * Show dividers between each item in the group.
     */
    public static final int SHOW_DIVIDER_MIDDLE = 2;
    /**
     * Show a divider at the end of the group.
     */
    public static final int SHOW_DIVIDER_END = 4;


    private Drawable mDivider;
    private int mDividerWidth;
    private int mShowDividers;
    private int mDividerPadding;


    public IcsLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs, /*com.android.internal.R.styleable.*/LinearLayout);

        setDividerDrawable(a.getDrawable(/*com.android.internal.R.styleable.*/LinearLayout_divider));
        mShowDividers = a.getInt(/*com.android.internal.R.styleable.*/LinearLayout_showDividers, SHOW_DIVIDER_NONE);
        mDividerPadding = a.getDimensionPixelSize(/*com.android.internal.R.styleable.*/LinearLayout_dividerPadding, 0);

        a.recycle();
    }

    /**
     * Set a drawable to be used as a divider between items.
     * @param divider Drawable that will divide each item.
     * @see #setShowDividers(int)
     */
    public void setDividerDrawable(Drawable divider) {
        if (divider == mDivider) {
            return;
        }
        mDivider = divider;
        if (divider != null) {
            mDividerWidth = divider.getIntrinsicWidth();
        } else {
            mDividerWidth = 0;
        }
        setWillNotDraw(divider == null);
        requestLayout();
    }

    /**
     * Get the width of the current divider drawable.
     *
     * @hide Used internally by framework.
     */
    public int getDividerWidth() {
        return mDividerWidth;
    }

    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed, int parentHeightMeasureSpec, int heightUsed) {
        final int index = indexOfChild(child);
        if (hasDividerBeforeChildAt(index)) {
            //Account for the divider by pushing everything left
            ((LayoutParams)child.getLayoutParams()).leftMargin = mDividerWidth;
        }
        super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mDivider != null) {
            final int count = getChildCount();
            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                if (child != null && child.getVisibility() != GONE) {
                    if (hasDividerBeforeChildAt(i)) {
                        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                        final int left = child.getLeft() - lp.leftMargin;
                        drawVerticalDivider(canvas, left);
                    }
                }
            }

            if (hasDividerBeforeChildAt(count)) {
                final View child = getChildAt(count - 1);
                int right = 0;
                if (child == null) {
                    right = getWidth() - getPaddingRight() - mDividerWidth;
                } else {
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                    right = child.getRight() + lp.rightMargin;
                }
                drawVerticalDivider(canvas, right);
            }
        }

        super.onDraw(canvas);
    }

    void drawVerticalDivider(Canvas canvas, int left) {
        mDivider.setBounds(left, getPaddingTop() + mDividerPadding,
                left + mDividerWidth, getHeight() - getPaddingBottom() - mDividerPadding);
        mDivider.draw(canvas);
    }

    /**
     * Determines where to position dividers between children.
     *
     * @param childIndex Index of child to check for preceding divider
     * @return true if there should be a divider before the child at childIndex
     * @hide Pending API consideration. Currently only used internally by the system.
     */
    protected boolean hasDividerBeforeChildAt(int childIndex) {
        if (childIndex == 0) {
            return (mShowDividers & SHOW_DIVIDER_BEGINNING) != 0;
        } else if (childIndex == getChildCount()) {
            return (mShowDividers & SHOW_DIVIDER_END) != 0;
        } else if ((mShowDividers & SHOW_DIVIDER_MIDDLE) != 0) {
            boolean hasVisibleViewBefore = false;
            for (int i = childIndex - 1; i >= 0; i--) {
                if (getChildAt(i).getVisibility() != GONE) {
                    hasVisibleViewBefore = true;
                    break;
                }
            }
            return hasVisibleViewBefore;
        }
        return false;
    }
}
