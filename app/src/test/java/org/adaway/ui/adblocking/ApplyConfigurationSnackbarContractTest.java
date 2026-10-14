package org.adaway.ui.adblocking;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ApplyConfigurationSnackbarContractTest {
    @Test
    public void applyFeedbackDistinguishesPendingInstallingSuccessAndFailure()
            throws Exception {
        String strings = readRepoFile("app/src/main/res/values/strings.xml");
        String snackbar = readRepoFile(
                "app/src/main/java/org/adaway/ui/adblocking/ApplyConfigurationSnackbar.java");
        String sources = readRepoFile(
                "app/src/main/java/org/adaway/ui/hosts/HostsSourcesFragment.java");

        assertTrue("Configuration apply feedback must include distinct success copy.",
                strings.contains("notification_configuration_changed") &&
                        strings.contains("notification_configuration_installing") &&
                        strings.contains("notification_configuration_applied") &&
                        strings.contains("notification_configuration_failed"));
        assertTrue("Successful source-toggle apply must show the applied confirmation.",
                snackbar.contains("R.string.notification_configuration_applied") &&
                        snackbar.contains("showApplied()"));
        assertFalse("Apply success must not silently dismiss the wait snackbar.",
                snackbar.contains("else if (this.update) {\n" +
                        "            // Ignore next update event if events should be ignored"));
        assertTrue("Sources update/apply success must use applied copy, not pending-apply copy.",
                sources.contains("this.mViewModel.getApplyStatus().observe(lifecycleOwner,") &&
                        snackbar.contains("status == ApplyStatus.SUCCEEDED) showApplied()") &&
                        !sources.contains("Snackbar.make(coordinatorLayout, " +
                                "R.string.notification_configuration_changed, " +
                                "Snackbar.LENGTH_LONG).show();"));
    }

    private static String readRepoFile(String relativePath) throws Exception {
        return new String(Files.readAllBytes(resolveRepoFile(relativePath)), StandardCharsets.UTF_8);
    }

    private static Path resolveRepoFile(String relativePath) {
        Path cwd = Paths.get("").toAbsolutePath();
        Path direct = cwd.resolve(relativePath);
        if (Files.exists(direct)) {
            return direct;
        }
        Path parent = cwd.getParent();
        while (parent != null) {
            Path candidate = parent.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            parent = parent.getParent();
        }
        return direct;
    }
}
