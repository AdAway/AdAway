package android.support.v4.view;

public interface Menu extends android.view.Menu {

    /**
     * This is the part of an order integer that the user can provide.
     * @hide
     */
    static final int USER_MASK = 0x0000ffff;
    /**
     * Bit shift of the user portion of the order integer.
     * @hide
     */
    static final int USER_SHIFT = 0;

    /**
     * This is the part of an order integer that supplies the category of the
     * item.
     * @hide
     */
    static final int CATEGORY_MASK = 0xffff0000;
    /**
     * Bit shift of the category portion of the order integer.
     * @hide
     */
    static final int CATEGORY_SHIFT = 16;

    /**
     * Value to use for group and item identifier integers when you don't care
     * about them.
     */
    static final int NONE = 0;

    /**
     * First value for group and item identifier integers.
     */
    static final int FIRST = 1;

    // Implementation note: Keep these CATEGORY_* in sync with the category enum
    // in attrs.xml

    /**
     * Category code for the order integer for items/groups that are part of a
     * container -- or/add this with your base value.
     */
    static final int CATEGORY_CONTAINER = 0x00010000;

    /**
     * Category code for the order integer for items/groups that are provided by
     * the system -- or/add this with your base value.
     */
    static final int CATEGORY_SYSTEM = 0x00020000;

    /**
     * Category code for the order integer for items/groups that are
     * user-supplied secondary (infrequently used) options -- or/add this with
     * your base value.
     */
    static final int CATEGORY_SECONDARY = 0x00030000;

    /**
     * Category code for the order integer for items/groups that are
     * alternative actions on the data that is currently displayed -- or/add
     * this with your base value.
     */
    static final int CATEGORY_ALTERNATIVE = 0x00040000;

    /**
     * Flag for {@link #addIntentOptions}: if set, do not automatically remove
     * any existing menu items in the same group.
     */
    static final int FLAG_APPEND_TO_GROUP = 0x0001;


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
