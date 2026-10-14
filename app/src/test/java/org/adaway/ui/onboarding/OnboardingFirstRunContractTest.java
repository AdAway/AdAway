package org.adaway.ui.onboarding;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class OnboardingFirstRunContractTest {
    @Test
    public void noRootAutoDetectPreselectsVpnWithoutSurprisePermissionPrompt()
            throws Exception {
        String onboarding = readRepoFile(
                "app/src/main/java/org/adaway/ui/onboarding/OnboardingActivity.java");
        String layout = readRepoFile("app/src/main/res/layout/activity_onboarding.xml");
        String homeActivity = readRepoFile(
                "app/src/main/java/org/adaway/ui/home/HomeActivity.java");
        String initialSources = readRepoFile(
                "app/src/main/java/org/adaway/ui/onboarding/InitialSourcesViewModel.java");

        assertTrue("Onboarding must start with an explicit disabled Start button.",
                layout.contains("android:id=\"@+id/onboarding_start_button\"") &&
                        layout.contains("android:enabled=\"false\""));
        assertTrue("No-root auto-detect must preselect VPN without launching permission.",
                getMethodBody(onboarding, "autoDetectAndPreselectMethod")
                        .contains("preselectVpn();") &&
                        !getMethodBody(onboarding, "autoDetectAndPreselectMethod")
                                .contains("trySelectVpn();"));
        assertTrue("Preselected VPN must update selection state and enable Start.",
                onboarding.contains("private void preselectVpn()") &&
                        getMethodBody(onboarding, "preselectVpn")
                                .contains("this.selectedMethod = AdBlockMethod.VPN") &&
                        getMethodBody(onboarding, "preselectVpn")
                                .contains("onboardingStartButton.setEnabled(true)") &&
                        getMethodBody(onboarding, "preselectVpn")
                                .contains("updateMethodCards();"));
        assertTrue("Starting VPN protection must request Android VPN consent before completion.",
                getMethodBody(onboarding, "startProtecting")
                        .contains("this.selectedMethod == AdBlockMethod.VPN") &&
                        onboarding.contains("private void startVpnProtection()") &&
                        getMethodBody(onboarding, "startVpnProtection")
                                .contains("VpnService.prepare(this)") &&
                        getMethodBody(onboarding, "startVpnProtection")
                                .contains("this.prepareVpnLauncher.launch(prepareIntent)"));
        assertTrue("VPN authorization callback must complete onboarding after accepted consent.",
                onboarding.contains("private boolean finishAfterVpnAuthorization") &&
                        getMethodBody(onboarding, "onVpnSelected")
                                .contains("finishOnboarding(AdBlockMethod.VPN)") &&
                        getMethodBody(onboarding, "onVpnSelected")
                                .contains("finishAfterVpnAuthorization"));
        assertTrue("Completing onboarding must save the method and launch Home with defaults flag.",
                onboarding.contains("private void finishOnboarding(AdBlockMethod method)") &&
                        getMethodBody(onboarding, "finishOnboarding")
                                .contains("PreferenceHelper.setAbBlockMethod(this, method)") &&
                        getMethodBody(onboarding, "finishOnboarding")
                                .contains("HomeActivity.EXTRA_ONBOARDING_COMPLETE"));
        assertTrue("Completing VPN onboarding must apply protection after accepted consent.",
                onboarding.contains("import org.adaway.AdAwayApplication;") &&
                        onboarding.contains("import org.adaway.model.adblocking.AdBlockModel;") &&
                        onboarding.contains("import org.adaway.model.error.HostErrorException;") &&
                        getMethodBody(onboarding, "finishOnboarding")
                                .contains("method == AdBlockMethod.VPN") &&
                        getMethodBody(onboarding, "finishOnboarding")
                                .contains("applyVpnProtection()") &&
                        getMethodBody(onboarding, "applyVpnProtection")
                                .contains("getAdBlockModel()") &&
                        getMethodBody(onboarding, "applyVpnProtection")
                                .contains("adBlockModel.apply()"));
        assertTrue("Home must prepare initial downloads only after onboarding completion.",
                homeActivity.contains("if (getIntent().getBooleanExtra(" +
                        "EXTRA_ONBOARDING_COMPLETE, false)) {") &&
                        homeActivity.contains("provider.get(InitialSourcesViewModel.class)") &&
                        initialSources.contains("DefaultListsSubscriber.prepareInitialSources"));
        assertTrue("Queued initialization must use application context and a retained Home model.",
                homeActivity.contains("HomeViewModel homeViewModel = " +
                        "provider.get(HomeViewModel.class)") &&
                        homeActivity.contains(".initialize(getApplicationContext(), " +
                                "homeViewModel::update)"));
    }

    private static String getMethodBody(String source, String methodName) {
        int start = source.indexOf("private void " + methodName + "(");
        if (start < 0) {
            start = source.indexOf("private boolean " + methodName + "(");
        }
        if (start < 0) {
            return "";
        }
        int brace = source.indexOf('{', start);
        if (brace < 0) {
            return "";
        }
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(brace, i + 1);
                }
            }
        }
        return source.substring(brace);
    }

    private static String readRepoFile(String relativePath) throws Exception {
        byte[] bytes = Files.readAllBytes(resolveRepoFile(relativePath));
        return new String(bytes, StandardCharsets.UTF_8);
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
