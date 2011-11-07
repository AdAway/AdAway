package com.actionbarsherlock.internal.view.menu;

import java.lang.ref.WeakReference;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.actionbarsherlock.R;

public class ActionMenuItemView extends RelativeLayout implements MenuView.ItemView, View.OnClickListener {
    private ImageView mImageButton;
    private TextView mTextButton;
    private FrameLayout mCustomView;
    private MenuItemImpl mMenuItem;
    private WeakReference<ImageView> mDivider;

    public ActionMenuItemView(Context context) {
        this(context, null);
    }
    public ActionMenuItemView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.actionButtonStyle);
    }
    public ActionMenuItemView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setOnClickListener(this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mImageButton = (ImageView) findViewById(R.id.abs__item_icon);
        mImageButton.setOnClickListener(this);
        mTextButton = (TextView) findViewById(R.id.abs__item_text);
        mTextButton.setOnClickListener(this);
        mCustomView = (FrameLayout) findViewById(R.id.abs__item_custom);
        mCustomView.setOnClickListener(this);
    }

    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mImageButton.setEnabled(enabled);
        mTextButton.setEnabled(enabled);
        mCustomView.setEnabled(enabled);
    }

    public void setDivider(ImageView divider) {
        mDivider = new WeakReference<ImageView>(divider);
        //Ensure we are not displaying the divider when we are not visible
        setDividerVisibility(getVisibility());
    }

    public void setVisible(boolean visible) {
        final int visibility = visible ? View.VISIBLE : View.GONE;
        setDividerVisibility(visibility);
        setVisibility(visibility);
    }

    private void setDividerVisibility(int visibility) {
        if ((mDivider != null) && (mDivider.get() != null)) {
            mDivider.get().setVisibility(visibility);
        }
    }

    public void reloadDisplay() {
        final boolean hasCustomView = mCustomView.getChildCount() > 0;
        final boolean hasText = mMenuItem.showsActionItemText() && !"".equals(mTextButton.getText());

        if (hasCustomView) {
            mCustomView.setVisibility(View.VISIBLE);
            mImageButton.setVisibility(View.GONE);
            mTextButton.setVisibility(View.GONE);
        } else {
            mCustomView.setVisibility(View.GONE);
            mImageButton.setVisibility(View.VISIBLE);
            mTextButton.setVisibility(hasText ? View.VISIBLE : View.GONE);
        }
    }

    public void setIcon(Drawable icon) {
        mImageButton.setImageDrawable(icon);
    }

    public void setTitle(CharSequence title) {
        mTextButton.setText(title);
        reloadDisplay();
    }

    @Override
    public void initialize(MenuItemImpl itemData, int menuType) {
        mMenuItem = itemData;
        setId(itemData.getItemId());
        setIcon(itemData.getIcon());
        setTitle(itemData.getTitle());
        setEnabled(itemData.isEnabled());
        setActionView(itemData.getActionView());
        setVisible(itemData.isVisible());
    }

    @Override
    public MenuItemImpl getItemData() {
        return mMenuItem;
    }

    @Override
    public void setCheckable(boolean checkable) {
        // No-op
    }

    @Override
    public void setChecked(boolean checked) {
        // No-op
    }

    @Override
    public void setShortcut(boolean showShortcut, char shortcutKey) {
        // No-op
    }

    @Override
    public void setActionView(View actionView) {
        mCustomView.removeAllViews();
        if (actionView != null) {
            mCustomView.addView(actionView);
        }
        reloadDisplay();
    }

    @Override
    public boolean prefersCondensedTitle() {
        return true;
    }

    @Override
    public boolean showsIcon() {
        return true;
    }

    @Override
    public void onClick(View v) {
        if (mMenuItem != null) {
            mMenuItem.invoke();
        }
    }
}
