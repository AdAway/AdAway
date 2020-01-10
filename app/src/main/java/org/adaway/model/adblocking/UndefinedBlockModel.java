package org.adaway.model.adblocking;

import android.content.Context;

import java.util.List;

import static java.util.Collections.emptyList;
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

    @Override
    public boolean isRecordingLogs() {
        return false;
    }

    @Override
    public void setRecordingLogs(boolean recording) {

    }

    @Override
    public List<String> getLogs() {
        return emptyList();
    }

    @Override
    public void clearLogs() {

    }
}
