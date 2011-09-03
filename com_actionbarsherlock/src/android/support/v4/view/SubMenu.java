package android.support.v4.view;

import android.graphics.drawable.Drawable;
import android.view.View;

public interface SubMenu extends android.view.SubMenu {
    @Override
    MenuItem getItem();

    @Override
    SubMenu setHeaderIcon(Drawable icon);

    @Override
    SubMenu setHeaderIcon(int iconRes);

    @Override
    SubMenu setHeaderTitle(CharSequence title);

    @Override
    SubMenu setHeaderTitle(int titleRes);

    @Override
    SubMenu setHeaderView(View view);

    @Override
    SubMenu setIcon(Drawable icon);

    @Override
    SubMenu setIcon(int iconRes);
}
