package org.adaway.model.source;

import org.adaway.db.entity.HostsSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Tracks source configuration, excluding download and presentation metadata. */
public final class SourceApplyState {
    private String mCurrentConfiguration;
    private String mAppliedConfiguration;
    private Map<Integer, String> mCurrentSources;
    private Map<Integer, String> mAppliedSources;
    private String mLastCompletedWork;
    private boolean mWorkObserved;
    private boolean mAcknowledgementUncertain;

    public void updateSources(List<HostsSource> sources) {
        if (sources == null) {
            return;
        }
        mCurrentSources = snapshot(sources);
        mCurrentConfiguration = fingerprint(mCurrentSources);
        if (mAppliedConfiguration == null) {
            mAppliedConfiguration = mCurrentConfiguration;
        }
        if (!mAcknowledgementUncertain && mCurrentConfiguration.equals(mAppliedConfiguration)) {
            mAppliedSources = new TreeMap<>(mCurrentSources);
        }
    }

    public boolean isPending() {
        return mCurrentConfiguration != null
                && (mAcknowledgementUncertain
                    || !mCurrentConfiguration.equals(mAppliedConfiguration));
    }

    public void invalidate() {
        mAcknowledgementUncertain = true;
        mAppliedSources = null;
    }

    public void applied(String configuration) {
        if (configuration != null) {
            mAcknowledgementUncertain = false;
            mAppliedConfiguration = configuration;
            mAppliedSources = configuration.equals(mCurrentConfiguration)
                    ? new TreeMap<>(mCurrentSources) : null;
        }
    }

    public void appliedSources(List<HostsSource> sources, Integer sourceId) {
        Map<Integer, String> completedSources = snapshot(sources);
        if (sourceId == null) {
            mAcknowledgementUncertain = false;
            mAppliedSources = completedSources;
        } else {
            if (mAcknowledgementUncertain || mAppliedSources == null) {
                return;
            }
            mAppliedSources = new TreeMap<>(mAppliedSources);
            String completedSource = completedSources.get(sourceId);
            if (completedSource == null) {
                mAppliedSources.remove(sourceId);
            } else {
                mAppliedSources.put(sourceId, completedSource);
            }
        }
        mAppliedConfiguration = fingerprint(mAppliedSources);
    }

    public boolean observeAutomaticWork(String workId, boolean successful, String configuration) {
        // A new screen must not replay historical success over a later manual apply.
        if (!mWorkObserved) {
            mWorkObserved = true;
            mLastCompletedWork = successful ? workId : null;
            return false;
        }
        if (successful && workId != null && !workId.equals(mLastCompletedWork)) {
            mLastCompletedWork = workId;
            if (configuration == null) {
                invalidate();
            } else {
                applied(configuration);
            }
            return true;
        }
        return false;
    }

    public static String fingerprint(List<HostsSource> sources) {
        return fingerprint(snapshot(sources));
    }

    private static Map<Integer, String> snapshot(List<HostsSource> sources) {
        Map<Integer, String> snapshot = new TreeMap<>();
        for (HostsSource source : sources) {
            String url = source.getUrl();
            String row = source.getId() + ":" + source.isEnabled() + ":"
                    + source.isAllowEnabled() + ":" + source.isRedirectEnabled() + ":"
                    + url.length() + ":" + url;
            snapshot.put(source.getId(), row);
        }
        return snapshot;
    }

    private static String fingerprint(Map<Integer, String> sources) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String row : sources.values()) {
                digest.update(row.getBytes(StandardCharsets.UTF_8));
            }
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest.digest()) {
                result.append(Character.forDigit((value >>> 4) & 15, 16));
                result.append(Character.forDigit(value & 15, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
