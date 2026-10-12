package org.adaway.security;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNoException;

public class ReleaseWorkflowRegressionTest {
    @Test
    public void releaseIdentityWorkflowPinsTestedBuildTools() throws Exception {
        String identity = step(read(".github/workflows/fork-release-apk.yml"),
                "Verify release APK identity");
        assertTrue(identity.contains("build-tools/36.0.0"));
        assertFalse(identity.contains("sort -V"));
        assertTrue(identity.contains("\"$APKSIGNER\" verify --print-certs"));
        assertTrue(identity.contains("\"$ACTUAL_CERT_SHA256\" != \"$EXPECTED_CERT_SHA256\""));
    }

    @Test
    public void releaseSmokePrefersTestedToolsOverNewerInstalledAndPath() throws Exception {
        releaseToolchainFixture(true, false);
    }

    @Test
    public void releaseSmokeRefusesMissingTestedSignerWithoutFallback() throws Exception {
        releaseToolchainFixture(false, false);
    }

    @Test
    public void releaseSmokeRejectsWrongCertificateWithPinnedTools() throws Exception {
        releaseToolchainFixture(true, true);
    }

    @Test
    public void androidSetupNeverUsesRetiredDefaultPackages() throws Exception {
        for (String file : List.of("android-ci.yml", "android-analysis.yml",
                "fork-release-apk.yml", "refresh-update-manifest.yml")) {
            String workflow = read(".github/workflows/" + file);
            for (String step : workflow.split("(?m)^\\s*- name:")) {
                if (step.contains("uses: android-actions/setup-android@")) {
                    assertTrue(file, step.contains("packages: platform-tools"));
                    assertFalse(file, step.contains("packages: tools"));
                }
            }
        }
    }

    @Test
    public void attestationCliStepHasExplicitAuthentication() throws Exception {
        String workflow = read(".github/workflows/fork-release-apk.yml");
        String step = workflow.substring(workflow.indexOf("- name: Verify release artifact attestations"),
                workflow.indexOf("- name: Create GitHub Release"));
        assertTrue(step.contains("GH_TOKEN: ${{ github.token }}"));
        assertTrue(step.contains("--verify-attestations"));
    }

    @Test
    public void certificatePinIsConfiguredFromExistingReleaseSecret() throws Exception {
        String workflow = read(".github/workflows/fork-release-apk.yml");
        assertTrue(workflow.contains("ANDROID_RELEASE_CERT_SHA256: ${{ secrets.ANDROID_RELEASE_CERT_SHA256 }}"));
        assertTrue(workflow.contains("releaseSigningCertificateSha256=%s"));
        String build = read("app/build.gradle");
        assertTrue(build.contains("project.findProperty('releaseSigningCertificateSha256')"));
        assertTrue(build.contains("releaseCertificateSha256 ==~ '[0-9A-F]{64}'"));
        assertTrue(read(".github/workflows/android-ci.yml")
                .contains("-PreleaseSigningCertificateSha256=\"$RELEASE_CERT_SHA256\""));
    }

    @Test
    public void weeklyRefreshVerifiesApkAndOnlyReplacesSignedMetadata() throws Exception {
        String workflow = read(".github/workflows/refresh-update-manifest.yml");
        assertTrue(workflow.contains("workflow_dispatch:"));
        assertTrue(workflow.contains("schedule:"));
        assertTrue(workflow.contains("channel: [stable, beta]"));
        assertTrue(workflow.contains("cron: '17 6 * * 2'"));
        assertTrue(workflow.contains("fail-fast: false"));
        assertTrue(workflow.contains("apksigner\" verify --print-certs"));
        assertTrue(workflow.contains("\"$ACTUAL_CERT\" != \"$EXPECTED_CERT\""));
        assertTrue(workflow.contains("\"$EXPECTED_SHA\" != \"$ACTUAL_SHA\""));
        assertTrue(workflow.contains("--valid-days 14"));
        assertTrue(workflow.contains("--clobber \\\n            refresh/manifest.json refresh/manifest.json.sha256"));
        assertTrue(workflow.contains("gh attestation verify refresh/manifest.json.sha256"));
        assertTrue(read("app/src/main/java/org/adaway/model/update/Manifest.java")
                .contains("MAX_VALIDITY_SECONDS = 14L"));
    }

    @Test
    public void minifiedStartupUsesOnlyDryRunApkAfterDebugSuite() throws Exception {
        String workflow = read(".github/workflows/android-ci.yml");
        String upload = step(workflow, "Upload minified startup APK");
        assertTrue(upload.contains("actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a"));
        assertTrue(upload.contains("name: AdAway-directRelease-dry-run"));
        assertTrue(upload.contains("path: app/build/outputs/apk/directRelease/app-directRelease.apk\n"));
        assertTrue(upload.contains("if-no-files-found: error"));
        assertFalse(upload.contains("path: |"));
        assertFalse(upload.contains("RUNNER_TEMP"));
        assertFalse(upload.contains(".jks"));
        assertFalse(upload.contains("secrets."));
        String download = step(workflow, "Download minified startup APK");
        assertTrue(download.contains("actions/download-artifact@d3f86a106a0bac45b974a628896c90dbdf5c8093"));
        assertTrue(download.contains("name: AdAway-directRelease-dry-run"));
        assertTrue(download.contains("path: app/build/minified-startup-apk"));
        String connected = workflow.substring(workflow.indexOf("  connected-tests:"));
        assertTrue(connected.contains("needs: build"));
        assertTrue(connected.indexOf("Download minified startup APK") >= 0);
        assertTrue(connected.indexOf("Check minified startup")
                > connected.indexOf(":app:connectedDebugAndroidTest"));
        String smoke = step(workflow, "Check minified startup");
        assertTrue(smoke.contains("timeout-minutes: 5"));
        assertTrue(smoke.contains("bash scripts/check-minified-startup.sh"));
        assertTrue(smoke.contains("app/build/minified-startup-apk/app-directRelease.apk"));
        assertTrue(smoke.contains("emulator-5554 app/build/ci-artifacts/minified-startup"));
        assertFalse(smoke.contains("continue-on-error"));
        assertFalse(smoke.contains("|| true"));
        String diagnostics = step(workflow, "Upload connected test artifacts");
        assertTrue(diagnostics.contains("if: always()"));
        assertTrue(diagnostics.contains("app/build/ci-artifacts/**"));
    }

    @Test
    public void roomGeneratedDatabaseKeepsReflectiveNoArgConstructor() throws Exception {
        String rules = read("app/proguard-rules.pro");
        assertTrue(rules.matches("(?s).*-keep class \\* extends androidx\\.room\\.RoomDatabase"
                + "\\s*\\{\\s*public <init>\\(\\);\\s*}.*"));
    }

    @Test
    public void workInputMergerKeepsReflectiveNoArgConstructor() throws Exception {
        String rules = read("app/proguard-rules.pro");
        assertTrue(rules.matches("(?s).*-keep class \\* extends androidx\\.work\\.InputMerger"
                + "\\s*\\{\\s*public <init>\\(\\);\\s*}.*"));
    }

    @Test
    public void minifiedStartupRefusesPhysicalDeviceBeforeChangingIt() throws Exception {
        smokeFixture("physical", false);
    }

    @Test
    public void minifiedStartupRejectsDebuggableApkBeforeDeviceAccess() throws Exception {
        smokeFixture("debug", false);
    }

    @Test
    public void minifiedStartupRejectsInvalidSignatureBeforeDeviceAccess() throws Exception {
        smokeFixture("bad-signature", false);
    }

    @Test
    public void acceptedLaunchWithoutProcessDoesNotPassStartup() throws Exception {
        smokeFixture("dead", false);
    }

    @Test
    public void backgroundProcessWithoutResumedAppDoesNotPassStartup() throws Exception {
        smokeFixture("background", false);
    }

    @Test
    public void stableProcessAndResumedOnboardingPassStartup() throws Exception {
        smokeFixture("ready", true);
    }

    private static void releaseToolchainFixture(boolean signerInstalled, boolean wrongCertificate)
            throws Exception {
        assumeFalse("Mock SDK tools use Bash", System.getProperty("os.name").startsWith("Windows"));
        Path fixture = Files.createTempDirectory("adaway-release-toolchain");
        try {
            Path sdk = fixture.resolve("sdk");
            Path bin = fixture.resolve("bin");
            String cert = "12".repeat(32);
            String aapt = "echo \"package: name='org.adaway' versionCode='130503'\"\n";
            String signer = "echo 'Signer #1 certificate SHA-256 digest: " + cert + "'\n";
            writeTool(sdk.resolve("build-tools/36.0.0/aapt"),
                    "echo tested-aapt >> \"$TOOL_CALLS\"\n" + aapt);
            if (signerInstalled) {
                writeTool(sdk.resolve("build-tools/36.0.0/apksigner"),
                        "echo tested-signer >> \"$TOOL_CALLS\"\n" + signer);
            }
            writeTool(sdk.resolve("build-tools/37.0.0/aapt"),
                    "echo newer-aapt >> \"$TOOL_CALLS\"\n" + aapt);
            writeTool(sdk.resolve("build-tools/37.0.0/apksigner"),
                    "echo newer-signer >> \"$TOOL_CALLS\"\n" + signer);
            writeTool(bin.resolve("apksigner"),
                    "echo path-signer >> \"$TOOL_CALLS\"\n" + signer);
            Path apk = fixture.resolve("release.apk");
            Files.write(apk, new byte[]{1});
            Path calls = fixture.resolve("calls.txt");
            Path output = fixture.resolve("output.txt");
            ProcessBuilder builder = new ProcessBuilder("pwsh", "-NoProfile", "-File",
                    root().resolve("scripts/run-release-smoke.ps1").toAbsolutePath().toString(),
                    "-ApkPath", apk.toString(), "-ExpectedCertSha256",
                    wrongCertificate ? "34".repeat(32) : cert, "-VerifyOnly");
            builder.environment().put("ANDROID_HOME", sdk.toString());
            builder.environment().put("ANDROID_SDK_ROOT", sdk.toString());
            builder.environment().put("PATH", bin + ":" + builder.environment().get("PATH"));
            builder.environment().put("TOOL_CALLS", calls.toString());
            Process process;
            try {
                process = builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
            } catch (IOException unavailable) {
                assumeNoException("PowerShell is required for the mock release verifier", unavailable);
                return;
            }
            try {
                assertTrue("Mock release verifier must finish", process.waitFor(20, TimeUnit.SECONDS));
                assertEquals(readFile(output), signerInstalled && !wrongCertificate,
                        process.exitValue() == 0);
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    process.waitFor(5, TimeUnit.SECONDS);
                }
            }
            assertEquals(signerInstalled ? "tested-aapt\ntested-signer\n" : "tested-aapt\n",
                    readFile(calls));
            if (!signerInstalled) {
                assertTrue(readFile(output).contains("Android build-tools 36.0.0"));
            } else if (wrongCertificate) {
                assertTrue(readFile(output).contains("fingerprint does not match"));
            }
        } finally {
            try (Stream<Path> paths = Files.walk(fixture)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) {
                    Files.delete(path);
                }
            }
        }
    }

    private static String step(String workflow, String name) {
        int start = workflow.indexOf("      - name: " + name + "\n");
        assertTrue("Missing workflow step: " + name, start >= 0);
        int next = workflow.indexOf("\n      - ", start + 1);
        return workflow.substring(start, next < 0 ? workflow.length() : next);
    }

    private static void smokeFixture(String mode, boolean expectedPass) throws Exception {
        assumeFalse("CI startup script uses Bash", System.getProperty("os.name").startsWith("Windows"));
        Path fixture = Files.createTempDirectory("adaway-minified-startup");
        try {
            Path sdk = fixture.resolve("sdk");
            Path bin = fixture.resolve("bin");
            writeTool(bin.resolve("timeout"), "shift\nexec \"$@\"\n");
            writeTool(bin.resolve("sleep"), "exit 0\n");
            writeTool(sdk.resolve("build-tools/36.0.0/aapt"),
                    "echo \"package: name='org.adaway' versionCode='130502'\"\n"
                            + "if [ \"$SMOKE_MODE\" = debug ]; then echo application-debuggable; fi\n");
            writeTool(sdk.resolve("build-tools/36.0.0/apksigner"),
                    "[ \"$SMOKE_MODE\" != bad-signature ]\n");
            writeTool(sdk.resolve("platform-tools/adb"), """
                    printf '%s\n' "$*" >> "$SMOKE_CALLS"
                    [ "$1" = -s ] && [ "$2" = emulator-5554 ] || exit 1
                    shift 2
                    case "$*" in
                      'shell getprop ro.kernel.qemu')
                        if [ "$SMOKE_MODE" = physical ]; then echo 0; else echo 1; fi ;;
                      'shell pm list packages org.adaway') echo package:org.adaway ;;
                      'uninstall org.adaway'|install*) echo Success ;;
                      'shell am force-stop org.adaway') ;;
                      'shell am start -W -n org.adaway/.ui.home.HomeActivity') echo 'Status: ok' ;;
                      'shell pidof org.adaway')
                        if [ "$SMOKE_MODE" != dead ]; then echo 4242; fi ;;
                      'shell dumpsys activity activities')
                        if [ "$SMOKE_MODE" = background ]; then
                          echo 'topResumedActivity=ActivityRecord{1 u0 com.android.launcher/.Home t1}'
                        else
                          echo 'topResumedActivity=ActivityRecord{1 u0 org.adaway/.ui.onboarding.OnboardingActivity t1}'
                        fi ;;
                      'shell dumpsys activity processes') echo diagnostics ;;
                      logcat*) echo 'crash diagnostics retained' ;;
                      *) exit 1 ;;
                    esac
                    """);
            Path apk = fixture.resolve("direct-release.apk");
            Files.write(apk, new byte[]{1});
            Path report = fixture.resolve("report");
            Path calls = fixture.resolve("calls.txt");
            Path output = fixture.resolve("output.txt");
            ProcessBuilder builder = new ProcessBuilder("bash",
                    root().resolve("scripts/check-minified-startup.sh").toAbsolutePath().toString(),
                    apk.toString(), "emulator-5554", report.toString());
            builder.environment().put("ANDROID_HOME", sdk.toString());
            builder.environment().put("PATH", bin + ":" + builder.environment().get("PATH"));
            builder.environment().put("SMOKE_MODE", mode);
            builder.environment().put("SMOKE_CALLS", calls.toString());
            Process process = builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
            try {
                assertTrue("Mock smoke must finish", process.waitFor(20, TimeUnit.SECONDS));
                assertEquals(readFile(output), expectedPass, process.exitValue() == 0);
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    process.waitFor(5, TimeUnit.SECONDS);
                }
            }
            String commands = Files.exists(calls) ? readFile(calls) : "";
            if (mode.equals("physical")) {
                assertEquals("-s emulator-5554 shell getprop ro.kernel.qemu\n", commands);
            } else if (mode.equals("debug") || mode.equals("bad-signature")) {
                assertEquals("Must reject unsafe APK before device access", "", commands);
            } else {
                assertTrue(commands.indexOf("shell getprop ro.kernel.qemu")
                        < commands.indexOf("uninstall org.adaway"));
                assertTrue(commands.contains("uninstall org.adaway"));
                assertTrue(commands.contains("shell am start -W -n org.adaway/.ui.home.HomeActivity"));
                assertTrue(readFile(report.resolve("crash-logcat.txt"))
                        .contains("crash diagnostics retained"));
                assertTrue(Files.exists(report.resolve("activities-final.txt")));
                assertTrue(Files.exists(report.resolve("processes-final.txt")));
            }
            assertTrue(readFile(report.resolve("result.txt"))
                    .contains("exit_code=" + (expectedPass ? "0" : "1")));
        } finally {
            try (Stream<Path> paths = Files.walk(fixture)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) {
                    Files.delete(path);
                }
            }
        }
    }

    private static void writeTool(Path path, String body) throws Exception {
        Files.createDirectories(path.getParent());
        Files.write(path, ("#!/usr/bin/env bash\n" + body).getBytes(StandardCharsets.UTF_8));
        assertTrue(path.toFile().setExecutable(true));
    }

    private static String readFile(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path root() {
        return Files.exists(Path.of("app/build.gradle")) ? Path.of("") : Path.of("..");
    }

    private static String read(String relative) throws Exception {
        return readFile(root().resolve(relative));
    }
}
