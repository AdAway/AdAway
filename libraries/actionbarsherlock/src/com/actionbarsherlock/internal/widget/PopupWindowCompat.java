
package com.actionbarsherlock.internal.widget;

import java.lang.reflect.Field;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnScrollChangedListener;
import android.widget.PopupWindow;

/**
 * Works around bugs in the handling of {@link ViewTreeObserver} by
 * {@link PopupWindow}.
 * <p>
 * <code>PopupWindow</code> registers an {@link OnScrollChangedListener} with
 * {@link ViewTreeObserver}, but does not keep a reference to the observer
 * instance that it has registers on. This is problematic when the anchor view
 * used by <code>PopupWindow</code> to access the observer is detached from the
 * window, as it will revert from the shared <code>ViewTreeObserver</code> owned
 * by the <code>ViewRoot</code> to a floating one, meaning
 * <code>PopupWindow</code> cannot unregister it's listener anymore and has
 * leaked it into the global observer.
 * <p>
 * This class works around this issue by
 * <ul>
 * <li>replacing <code>PopupWindow.mOnScrollChangedListener</code> with a no-op
 * listener so that any registration or unregistration performed by
 * <code>PopupWindow</code> itself has no effect and causes no leaks.
 * <li>registering the real listener only with the shared
 * <code>ViewTreeObserver</code> and keeping a reference to it to facilitate
 * correct unregistration. The reason for not registering on a floating observer
 * (before a view is attached) is that there is no safe way to get a reference
 * to the shared observer that the floating one will be merged into. This would
 * again cause the listener to leak.
 * </ul>
 */
public class PopupWindowCompat extends PopupWindow {

    private static final Field superListenerField;
    static {
        Field f = null;
        try {
            f = PopupWindow.class.getDeclaredField("mOnScrollChangedListener");
            f.setAccessible(true);
        } catch (NoSuchFieldException e) {
            /* ignored */
        }
        superListenerField = f;
    }

    private static final OnScrollChangedListener NOP = new OnScrollChangedListener() {
        @Override
        public void onScrollChanged() {
            /* do nothing */
        }
    };

    private OnScrollChangedListener mSuperScrollListener;
    private ViewTreeObserver mViewTreeObserver;

    public PopupWindowCompat() {
        super();
        init();
    }

    public PopupWindowCompat(Context context) {
        super(context);
        init();
    }

    public PopupWindowCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PopupWindowCompat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    // @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public PopupWindowCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public PopupWindowCompat(int width, int height) {
        super(width, height);
        init();
    }

    public PopupWindowCompat(View contentView) {
        super(contentView);
        init();
    }

    public PopupWindowCompat(View contentView, int width, int height, boolean focusable) {
        super(contentView, width, height, focusable);
        init();
    }

    public PopupWindowCompat(View contentView, int width, int height) {
        super(contentView, width, height);
        init();
    }

    private void init() {
        if (superListenerField != null) {
            try {
                mSuperScrollListener = (OnScrollChangedListener) superListenerField.get(this);
                superListenerField.set(this, NOP);
            } catch (Exception e) {
                mSuperScrollListener = null;
            }
        }
    }

    private void unregisterListener() {
        // Don't do anything if we haven't managed to patch the super listener
        if (mSuperScrollListener != null && mViewTreeObserver != null) {
            if (mViewTreeObserver.isAlive()) {
                mViewTreeObserver.removeOnScrollChangedListener(mSuperScrollListener);
            }
            mViewTreeObserver = null;
        }
    }

    private void registerListener(View anchor) {
        // Don't do anything if we haven't managed to patch the super listener.
        // And don't bother attaching the listener if the anchor view isn't
        // attached. This means we'll only have to deal with the real VTO owned
        // by the ViewRoot.
        if (mSuperScrollListener != null) {
            ViewTreeObserver vto = (anchor.getWindowToken() != null) ? anchor.getViewTreeObserver()
                    : null;
            if (vto != mViewTreeObserver) {
                if (mViewTreeObserver != null && mViewTreeObserver.isAlive()) {
                    mViewTreeObserver.removeOnScrollChangedListener(mSuperScrollListener);
                }
                if ((mViewTreeObserver = vto) != null) {
                    vto.addOnScrollChangedListener(mSuperScrollListener);
                }
            }
        }
    }

    @Override
    public void showAsDropDown(View anchor, int xoff, int yoff) {
        super.showAsDropDown(anchor, xoff, yoff);
        registerListener(anchor);
    }

    @Override
    public void update(View anchor, int xoff, int yoff, int width, int height) {
        super.update(anchor, xoff, yoff, width, height);
        registerListener(anchor);
    }

    @Override
    public void update(View anchor, int width, int height) {
        super.update(anchor, width, height);
        registerListener(anchor);
    }

    @Override
    public void showAtLocation(View parent, int gravity, int x, int y) {
        super.showAtLocation(parent, gravity, x, y);
        unregisterListener();
    }

    @Override
    public void dismiss() {
        super.dismiss();
        unregisterListener();
    }
}
