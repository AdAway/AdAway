package org.adaway.model.update;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;

public class UpdateStoreTest {
    private static final String TEST_CERT = "12".repeat(32);

    @Test
    public void configuredDirectSignerIsRecognizedWithoutAnUpstreamKey() {
        assertEquals(UpdateStore.ADAWAY,
                UpdateStore.fromCertificateDigests(Set.of(TEST_CERT), true, TEST_CERT));
    }

    @Test
    public void missingMalformedAndMismatchedPinsFailClosed() {
        for (String configured : new String[]{"", "invalid", "34".repeat(32)}) {
            assertEquals(UpdateStore.UNKNOWN,
                    UpdateStore.fromCertificateDigests(Set.of(TEST_CERT), true, configured));
        }
        assertEquals(UpdateStore.UNKNOWN,
                UpdateStore.fromCertificateDigests(Set.of(), true, TEST_CERT));
    }

    @Test
    public void upstreamKeyDoesNotBypassConfiguredDirectReleasePin() {
        assertEquals(UpdateStore.UNKNOWN, UpdateStore.fromCertificateDigests(
                Set.of(UpdateStore.ADAWAY.sign), true, TEST_CERT));
    }

    @Test
    public void fdroidAndOrdinaryBuildsRemainExcluded() {
        assertEquals(UpdateStore.F_DROID, UpdateStore.fromCertificateDigests(
                Set.of(UpdateStore.F_DROID.sign), true, UpdateStore.F_DROID.sign));
        assertEquals(UpdateStore.UNKNOWN,
                UpdateStore.fromCertificateDigests(Set.of(TEST_CERT), false, TEST_CERT));
    }

    @Test
    public void formattedFingerprintIsNormalized() {
        String digest = "AB".repeat(32);
        assertEquals(UpdateStore.ADAWAY, UpdateStore.fromCertificateDigests(
                Set.of(digest), true, ("ab:".repeat(31) + "ab\n")));
    }
}
