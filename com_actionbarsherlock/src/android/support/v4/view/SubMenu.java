package android.support.v4.view;

import android.graphics.drawable.Drawable;
import android.view.View;

public interface SubMenu extends android.view.SubMenu, Menu {
    @Override
    MenuItem add(CharSequence title);

    @Override
    MenuItem add(int groupId, int itemId, int order, int titleRes);

    @Override
    MenuItem add(int titleRes);

    @Override
    MenuItem add(int groupId, int itemId, int order, CharSequence title);

    @Override
    SubMenu addSubMenu(int groupId, int itemId, int order, CharSequence title);

    @Override
    SubMenu addSubMenu(int groupId, int itemId, int order, int titleRes);

    @Override
    SubMenu addSubMenu(CharSequence title);

    @Override
    SubMenu addSubMenu(int titleRes);

    @Override
    MenuItem findItem(int id);

    @Override
    MenuItem getItem(int index);

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
