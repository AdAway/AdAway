package org.adaway.ui.hosts;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class HostsSourcesActionsContractTest {
    @Test
    public void updateAllMenuPathUpdatesSourcesThenAppliesProtection()
            throws Exception {
        String menu = readRepoFile("app/src/main/res/menu/hosts_sources_menu.xml");
        String strings = readRepoFile("app/src/main/res/values/strings.xml");
        String fragment = readRepoFile(
                "app/src/main/java/org/adaway/ui/hosts/HostsSourcesFragment.java");

        String viewModel = readRepoFile(
                "app/src/main/java/org/adaway/ui/hosts/HostsSourcesViewModel.java");
        String snackbar = readRepoFile(
                "app/src/main/java/org/adaway/ui/adblocking/ApplyConfigurationSnackbar.java");

        assertTrue("Sources menu must expose update-all through a stable action id.",
                menu.contains("android:id=\"@+id/action_hosts_update_all\"") &&
                        menu.contains("android:title=\"@string/menu_update_all\""));
        assertTrue("Update-all copy must disclose that protection is applied.",
                strings.contains("<string name=\"menu_update_all\">" +
                        "Update all and apply protection</string>") &&
                        strings.contains("sources_apply_installing"));
        assertTrue("Sources menu handler must route update-all through updateAllSources().",
                fragment.contains("id == R.id.action_hosts_update_all") &&
                        fragment.contains("updateAllSources();"));
        assertTrue("Update all must run the all-sources branch, not a stale single-source path.",
                fragment.contains("public void updateAllSources()") &&
                        fragment.contains("runUpdateSources(null)") &&
                        fragment.contains("this.mViewModel.updateSources(source == null ? null : source.getId())") &&
                        viewModel.contains("shouldApply = sourceModel.checkAndRetrieveHostsSources();") &&
                        viewModel.contains("if (shouldApply) {") &&
                        viewModel.contains("adBlockModel.apply();"));
        assertTrue("Update all must show source-specific running feedback.",
                snackbar.contains("R.string.sources_apply_installing"));
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
