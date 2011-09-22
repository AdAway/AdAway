package com.actionbarsherlock.internal.view.menu;

import android.content.Context;

public final class MenuInflaterWrapper extends android.view.MenuInflater {
    private final android.view.MenuInflater mMenuInflater;

    public MenuInflaterWrapper(Context context, android.view.MenuInflater menuInflater) {
        super(context);
        mMenuInflater = menuInflater;
    }

    @Override
    public void inflate(int menuRes, android.view.Menu menu) {
        if (menu instanceof MenuWrapper) {
            mMenuInflater.inflate(menuRes, ((MenuWrapper)menu).unwrap());
        } else {
            mMenuInflater.inflate(menuRes, menu);
        }
    }
}
