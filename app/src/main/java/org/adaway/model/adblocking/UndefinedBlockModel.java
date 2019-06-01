package org.adaway.model.adblocking;

import android.content.Context;

import static org.adaway.model.adblocking.AdBlockMethod.UNDEFINED;

/**
 * This class is a stub model when no ad block method is defined.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class UndefinedBlockModel extends AdBlockModel {
    /**
     * Constructor.
     *
     * @param context The application context.
     */
    public UndefinedBlockModel(Context context) {
        super(context);
    }

    @Override
    public AdBlockMethod getMethod() {
        return UNDEFINED;
    }

    @Override
    public void apply() {

    }

    @Override
    public void revert() {

    }
}
