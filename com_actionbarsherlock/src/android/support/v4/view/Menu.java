package android.support.v4.view;

public interface Menu extends android.view.Menu {
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
}
