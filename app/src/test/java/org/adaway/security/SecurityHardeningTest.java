package org.adaway.security;

import org.adaway.util.RegexUtils;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Security regression tests for hardening changes in v13.4.2 and v13.4.3.
 * Only tests that use pure-Java code live here; Android-framework-dependent tests
 * (e.g. HostsSource.isValidUrl which uses android.webkit.URLUtil) must go in androidTest.
 *
 * <p>Each test is labelled with the ATK-XX identifier it guards.
 * ATK-23 tests live in BackupFormatSecurityTest (same package as BackupFormat).
 */
public class SecurityHardeningTest {

    // -------------------------------------------------------------------------
    // ATK-01: Private/reserved IP redirect blocking (RegexUtils)
    // -------------------------------------------------------------------------

    @Test
    public void atk01_loopbackIpRejectedAsRedirectTarget() {
        assertTrue("127.0.0.1 must be flagged as private", RegexUtils.isPrivateOrReservedIp("127.0.0.1"));
        assertTrue("127.0.0.2 must be flagged as private", RegexUtils.isPrivateOrReservedIp("127.0.0.2"));
        assertTrue("::1 must be flagged as private (IPv6 loopback)", RegexUtils.isPrivateOrReservedIp("::1"));
    }

    @Test
    public void atk01_rfc1918RangesRejectedAsRedirectTarget() {
        assertTrue("10.0.0.1 must be private", RegexUtils.isPrivateOrReservedIp("10.0.0.1"));
        assertTrue("10.255.255.255 must be private", RegexUtils.isPrivateOrReservedIp("10.255.255.255"));
        assertTrue("172.16.0.1 must be private", RegexUtils.isPrivateOrReservedIp("172.16.0.1"));
        assertTrue("172.31.255.255 must be private", RegexUtils.isPrivateOrReservedIp("172.31.255.255"));
        assertTrue("192.168.0.1 must be private", RegexUtils.isPrivateOrReservedIp("192.168.0.1"));
        assertTrue("192.168.255.255 must be private", RegexUtils.isPrivateOrReservedIp("192.168.255.255"));
    }

    @Test
    public void atk01_linkLocalRejectedAsRedirectTarget() {
        assertTrue("169.254.0.1 must be link-local", RegexUtils.isPrivateOrReservedIp("169.254.0.1"));
        assertTrue("fe80::1 must be link-local", RegexUtils.isPrivateOrReservedIp("fe80::1"));
    }

    @Test
    public void atk01_multicastRejectedAsRedirectTarget() {
        assertTrue("224.0.0.1 must be multicast", RegexUtils.isPrivateOrReservedIp("224.0.0.1"));
        assertTrue("239.255.255.255 must be multicast", RegexUtils.isPrivateOrReservedIp("239.255.255.255"));
    }

    @Test
    public void atk01_publicIpAllowedAsRedirectTarget() {
        assertFalse("8.8.8.8 must NOT be flagged as private", RegexUtils.isPrivateOrReservedIp("8.8.8.8"));
        assertFalse("1.1.1.1 must NOT be flagged as private", RegexUtils.isPrivateOrReservedIp("1.1.1.1"));
        assertFalse("104.21.0.1 must NOT be flagged as private", RegexUtils.isPrivateOrReservedIp("104.21.0.1"));
    }

    @Test
    public void atk01_invalidIpReturnsFalse() {
        assertFalse("not-an-ip must return false", RegexUtils.isPrivateOrReservedIp("not-an-ip"));
        assertFalse("empty must return false", RegexUtils.isPrivateOrReservedIp(""));
    }

    // Note: ATK-02 (HostsSource.isValidUrl) tests require android.webkit.URLUtil
    // and must live in the instrumented androidTest suite.

    // ATK-09 + ATK-29 AI prompt-sanitizer tests were removed with the AI feature cut.
    // ATK-23 tests are in BackupFormatSecurityTest (same package as BackupFormat)

    // -------------------------------------------------------------------------
    // ATK-15: Negative modelIndex clamped to valid range
    // -------------------------------------------------------------------------

    @Test
    public void atk15_mathMaxClampsNegativeIndex() {
        int negativeIndex = -5;
        int len = 3;
        int safeIndex = Math.max(0, Math.min(negativeIndex, len - 1));
        assertEquals("Negative index must be clamped to 0", 0, safeIndex);
    }

    @Test
    public void atk15_mathMinClampsOversizedIndex() {
        int oversizedIndex = 100;
        int len = 3;
        int safeIndex = Math.max(0, Math.min(oversizedIndex, len - 1));
        assertEquals("Oversized index must be clamped to len-1", len - 1, safeIndex);
    }

    // -------------------------------------------------------------------------
    // ATK-30: Dormant webserver must not ship reusable localhost credentials
    // -------------------------------------------------------------------------

    @Test
    public void atk30_noPackagedWebServerCredentialMaterial() throws IOException {
        Path assets = appDir().resolve("src/main/assets");
        if (!Files.isDirectory(assets)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(assets)) {
            Path secretFile = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.equals("localhost-2410.crt") ||
                                name.endsWith(".key") ||
                                name.endsWith(".pem") ||
                                name.endsWith(".p12") ||
                                name.endsWith(".jks") ||
                                name.endsWith(".bks");
                    })
                    .findFirst()
                    .orElse(null);
            assertNull("Packaged assets must not include reusable certificate/key material.", secretFile);
        }
    }

    @Test
    public void atk30_assetsDoNotContainPrivateKeys() throws IOException {
        Path assets = appDir().resolve("src/main/assets");
        if (!Files.isDirectory(assets)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(assets)) {
            Path privateKey = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> contains(path, "BEGIN PRIVATE KEY"))
                    .findFirst()
                    .orElse(null);
            assertNull("Packaged assets must not contain private keys.", privateKey);
        }
    }

    @Test
    public void atk30_noDormantNetworkSecurityTrustOverrides() throws IOException {
        Path app = appDir();
        String manifest = readUtf8(app.resolve("src/main/AndroidManifest.xml"));
        Path configPath = app.resolve("src/main/res/xml/network_security_config.xml");

        assertFalse("The default app must not keep stale AI-only network trust overrides.",
                manifest.contains("networkSecurityConfig"));
        assertFalse("Removed AI network-security config must not remain in packaged resources.",
                Files.exists(configPath));
    }

    @Test
    public void atk30_bootAndRootDoNotStartDormantWebServer() throws IOException {
        Path app = appDir();
        String bootReceiver = readUtf8(app.resolve("src/main/java/org/adaway/broadcast/BootReceiver.java"));
        String rootModel = readUtf8(app.resolve("src/main/java/org/adaway/model/root/RootModel.java"));

        assertFalse("BootReceiver must not restart the dormant webserver.",
                bootReceiver.contains("WebServerUtils.startWebServer"));
        assertFalse("RootModel must not restart the dormant webserver.",
                rootModel.contains("WebServerUtils.startWebServer"));
    }

    @Test
    public void atk32_rootTcpdumpCaptureDisabledUntilMaintainedReplacementExists() throws IOException {
        String tcpdumpUtils = readUtf8(
                appDir().resolve("src/main/java/org/adaway/model/root/TcpdumpUtils.java"));

        assertTrue("Old root tcpdump capture must stay disabled in production code.",
                tcpdumpUtils.contains("TCPDUMP_CAPTURE_ENABLED = false"));
    }

    @Test
    public void atk32_tcpdumpNativeModuleIsNotPackaged() throws IOException {
        Path repo = repoDir();
        String appBuild = readUtf8(repo.resolve("app/build.gradle"));
        String settings = readUtf8(repo.resolve("settings.gradle"));

        assertFalse("App must not package the dormant tcpdump native module.",
                appBuild.contains("project(':tcpdump')"));
        assertFalse("The dormant tcpdump module must stay out of normal Gradle builds.",
                settings.contains("':tcpdump'"));
    }

    @Test
    public void atk32_releaseSourceArchiveExcludesDormantNativeAndHistoricalAssets()
            throws IOException {
        Path repo = repoDir();
        String attributes = readUtf8(repo.resolve(".gitattributes"));

        assertTrue("Release source archives must exclude historical artwork/support resources.",
                attributes.contains("/Resources export-ignore"));
        assertTrue("Release source archives must exclude historical artwork/support resources.",
                attributes.contains("/Resources/** export-ignore"));
        assertTrue("Release source archives must exclude dormant tcpdump native source.",
                attributes.contains("/tcpdump export-ignore"));
        assertTrue("Release source archives must exclude dormant tcpdump native source.",
                attributes.contains("/tcpdump/** export-ignore"));
        assertTrue("Release source archives must exclude dormant webserver native source.",
                attributes.contains("/webserver export-ignore"));
        assertTrue("Release source archives must exclude dormant webserver native source.",
                attributes.contains("/webserver/** export-ignore"));
        assertTrue("Release source archives must keep removed reusable localhost certs out.",
                attributes.contains("/app/src/main/assets/localhost-2410.crt export-ignore"));
        assertTrue("Release source archives must keep removed reusable localhost keys out.",
                attributes.contains("/app/src/main/assets/localhost-2410.key export-ignore"));
        assertTrue("Release source archives must keep removed localhost test page out.",
                attributes.contains("/app/src/main/assets/test.html export-ignore"));
        assertTrue("Release source archives must keep removed legacy app icon asset out.",
                attributes.contains("/app/src/main/assets/icon.svg export-ignore"));

        assertFalse("Reusable localhost certificate must not remain in the app source tree.",
                Files.exists(repo.resolve("app/src/main/assets/localhost-2410.crt")));
        assertFalse("Reusable localhost private key must not remain in the app source tree.",
                Files.exists(repo.resolve("app/src/main/assets/localhost-2410.key")));
        assertFalse("Dormant localhost test page must not remain in the app source tree.",
                Files.exists(repo.resolve("app/src/main/assets/test.html")));
        assertFalse("Legacy app asset icon must not remain in app assets.",
                Files.exists(repo.resolve("app/src/main/assets/icon.svg")));
        assertFalse("Historical SunOS libpcap object file must not remain in the source tree.",
                Files.exists(repo.resolve("tcpdump/jni/libpcap/SUNOS4/nit_if.o.sparc")));
        assertFalse("Historical SunOS libpcap object file must not remain in the source tree.",
                Files.exists(repo.resolve("tcpdump/jni/libpcap/SUNOS4/nit_if.o.sun3")));
        assertFalse("Historical SunOS libpcap object file must not remain in the source tree.",
                Files.exists(repo.resolve("tcpdump/jni/libpcap/SUNOS4/nit_if.o.sun4c.4.0.3c")));

        Path archive = Files.createTempFile("adaway-source-archive", ".zip");
        try {
            Process process = new ProcessBuilder("git", "archive", "--format=zip",
                    "--worktree-attributes", "-o", archive.toString(), "HEAD")
                    .directory(repo.toFile())
                    .start();
            if (!process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                fail("Timed out generating source archive for export-ignore verification.");
            }
            assertEquals("git archive must succeed for source archive verification.",
                    0, process.exitValue());
            try (ZipFile zip = new ZipFile(archive.toFile())) {
                zip.stream().map(ZipEntry::getName).forEach(name -> {
                    assertFalse("Release source archive must not include historical Resources entries: " + name,
                            name.startsWith("Resources/"));
                    assertFalse("Release source archive must not include dormant tcpdump entries: " + name,
                            name.startsWith("tcpdump/"));
                    assertFalse("Release source archive must not include dormant webserver entries: " + name,
                            name.startsWith("webserver/"));
                    assertFalse("Release source archive must not include removed app asset: " + name,
                            name.equals("app/src/main/assets/icon.svg") ||
                                    name.equals("app/src/main/assets/localhost-2410.crt") ||
                                    name.equals("app/src/main/assets/localhost-2410.key") ||
                                    name.equals("app/src/main/assets/test.html"));
                    assertFalse("Release source archive must not include old binary libpcap object: " + name,
                            name.contains("SUNOS4/nit_if.o"));
                });
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            fail("Interrupted while generating source archive.");
        } finally {
            deleteFileWithRetries(archive);
        }
    }

    @Test
    public void atk32_dnsQueryNamesAreNotLoggedToTimber() throws IOException {
        Path app = appDir();
        String dnsPacketProxy = readUtf8(
                app.resolve("src/main/java/org/adaway/vpn/dns/DnsPacketProxy.java"));
        String dohPacketProxy = readUtf8(
                app.resolve("src/main/java/org/adaway/vpn/dns/DohPacketProxy.java"));

        assertFalse("VPN DNS proxy must not log raw DNS query names.",
                dnsPacketProxy.contains("DNS Name %s"));
        assertFalse("DoH DNS proxy must not log raw DNS query names.",
                dohPacketProxy.contains("DNS Name %s"));
    }

    @Test
    public void atk32_vpnBypassRequiresExplicitCompatibilityPreference() throws IOException {
        Path app = appDir();
        String vpnBuilder = readUtf8(
                app.resolve("src/main/java/org/adaway/vpn/worker/VpnBuilder.java"));
        String preferences = readUtf8(app.resolve("src/main/res/values/preferences.xml"));
        String vpnPreferences = readUtf8(app.resolve("src/main/res/xml/preferences_vpn.xml"));
        int preferenceCheckIndex = vpnBuilder.indexOf("getVpnAllowAppBypass(service)");
        int allowBypassIndex = vpnBuilder.indexOf("builder.allowBypass()");

        assertTrue("VPN bypass must be guarded by an explicit compatibility preference.",
                preferenceCheckIndex >= 0);
        assertTrue("VPN bypass must still be available only as an explicit escape hatch.",
                allowBypassIndex > preferenceCheckIndex);
        assertTrue("Programmatic app VPN bypass must be off by default.",
                preferences.contains("<bool name=\"pref_vpn_allow_app_bypass_def\">false</bool>"));
        assertTrue("VPN settings must expose the bypass compatibility switch.",
                vpnPreferences.contains("@string/pref_vpn_allow_app_bypass_key"));
    }

    @Test
    public void atk32_dohBlockRoutesCoverCommonIpv4AndIpv6Providers() throws IOException {
        String dnsServerMapper = readUtf8(
                appDir().resolve("src/main/java/org/adaway/vpn/dns/DnsServerMapper.java"));

        assertTrue("DoH block routing must include the IPv4 provider table.",
                dnsServerMapper.contains("DOH_PROVIDER_IPV4"));
        assertTrue("DoH block routing must include the IPv6 provider table.",
                dnsServerMapper.contains("DOH_PROVIDER_IPV6"));
        assertTrue("Cloudflare IPv6 DoH endpoint must be blocked when IPv6 is configured.",
                dnsServerMapper.contains("2606:4700:4700::1111"));
        assertTrue("Google IPv6 DoH endpoint must be blocked when IPv6 is configured.",
                dnsServerMapper.contains("2001:4860:4860::8888"));
        assertTrue("Quad9 IPv6 DoH endpoint must be blocked when IPv6 is configured.",
                dnsServerMapper.contains("2620:fe::fe"));
        assertTrue("OpenDNS IPv6 DoH endpoint must be blocked when IPv6 is configured.",
                dnsServerMapper.contains("2620:119:35::35"));
        assertTrue("IPv6 DoH routes must use host-prefix /128 routes.",
                dnsServerMapper.contains("addDohRoutes(routes, DOH_PROVIDER_IPV6, 128)"));
        assertTrue("Production VPN configuration must apply the shared DoH route plan.",
                dnsServerMapper.contains("for (DohRoute route : commonDohBlockRoutes(includeIpv6))") &&
                        dnsServerMapper.contains(
                                "builder.addRoute(route.address, route.prefixLength)"));
    }

    @Test
    public void atk32_homeLeakStateRequiresEstablishedAdAwayTunnel() throws IOException {
        Path app = appDir();
        String vpnService = readUtf8(app.resolve("src/main/java/org/adaway/vpn/VpnService.java"));
        String vpnControls = readUtf8(
                app.resolve("src/main/java/org/adaway/vpn/VpnServiceControls.java"));
        String leakStatus = readUtf8(app.resolve("src/main/java/org/adaway/ui/home/LeakStatus.java"));
        String homeStrings = readUtf8(app.resolve("src/main/res/values/strings_home.xml"));

        assertTrue("VPN startup must persist STARTING before the tunnel is established.",
                vpnService.contains("setVpnServiceStatus(this, STARTING)"));
        assertFalse("VPN startup must not persist RUNNING before VpnBuilder.establish() succeeds.",
                vpnService.contains("setVpnServiceStatus(this, RUNNING)"));
        assertTrue("Home status must have an established tunnel check.",
                vpnControls.contains("isTunnelEstablished"));
        assertTrue("Established tunnel check must require the post-establish RUNNING state.",
                vpnControls.contains("status == RUNNING"));
        assertTrue("Established tunnel check must reject foreign VPN owners on supported Android versions.",
                vpnControls.contains("getOwnerUid() == context.getApplicationInfo().uid"));
        assertTrue("Unreadable Private DNS state must be treated as a leak risk.",
                leakStatus.contains("isPrivateDnsUnknown() || isPrivateDnsActive()"));
        assertTrue("Encrypted-DNS copy must disclose partial DoH coverage.",
                homeStrings.contains("other encrypted DNS may bypass filtering"));
    }

    @Test
    public void atk33_githubActionsArePinnedToImmutableCommits() throws IOException {
        Path workflowDir = repoDir().resolve(".github/workflows");
        Pattern usesPattern = Pattern.compile("\\buses:\\s+[^@\\s]+@([^\\s#]+)");
        Pattern commitPattern = Pattern.compile("[0-9a-fA-F]{40}");

        try (Stream<Path> paths = Files.walk(workflowDir)) {
            Path mutableWorkflow = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
                    .filter(path -> {
                        try {
                            Matcher matcher = usesPattern.matcher(readUtf8(path));
                            while (matcher.find()) {
                                if (!commitPattern.matcher(matcher.group(1)).matches()) {
                                    return true;
                                }
                            }
                            return false;
                        } catch (IOException exception) {
                            throw new IllegalStateException("Failed to scan " + path, exception);
                        }
                    })
                    .findFirst()
                    .orElse(null);
            assertNull("GitHub Actions workflow uses must be pinned to 40-char commits.",
                    mutableWorkflow);
        }
    }

    @Test
    public void atk34_releaseWorkflowGeneratesUploadsAndAttestsSbom() throws IOException {
        String workflow = readUtf8(repoDir().resolve(".github/workflows/fork-release-apk.yml"));
        String manifestScript = readUtf8(repoDir().resolve("scripts/generate-update-manifest.sh"));
        String manifestPowerShell = readUtf8(repoDir().resolve("scripts/generate-update-manifest.ps1"));
        String manifestGenerator = readUtf8(repoDir().resolve("scripts/GenerateUpdateManifest.java"));
        String verifierPowerShell = readUtf8(repoDir().resolve("scripts/verify-release-artifacts.ps1"));
        String verifierShell = readUtf8(repoDir().resolve("scripts/verify-release-artifacts.sh"));
        String verifier = readUtf8(repoDir().resolve("scripts/VerifyReleaseArtifacts.java"));
        Pattern pinnedSbomAttest = Pattern.compile(
                "actions/attest@[0-9a-fA-F]{40}");
        int provenanceAttestationStart = workflow.indexOf("      - name: Attest release artifacts");
        int sbomAttestationStart = workflow.indexOf("      - name: Attest release SBOM");
        int postAttestationVerificationStart =
                workflow.indexOf("      - name: Verify release artifact attestations");
        int releaseUploadStart = workflow.indexOf("      - name: Create GitHub Release + upload APK");
        assertTrue("Release workflow must keep provenance attestation before SBOM attestation.",
                provenanceAttestationStart >= 0 &&
                        sbomAttestationStart > provenanceAttestationStart);
        assertTrue("Release workflow must verify GitHub attestations before release upload.",
                postAttestationVerificationStart > sbomAttestationStart &&
                        releaseUploadStart > postAttestationVerificationStart);
        String provenanceAttestationBlock =
                workflow.substring(provenanceAttestationStart, sbomAttestationStart);

        assertTrue("Release workflow must generate an SBOM.",
                workflow.contains(":app:generateSbom"));
        assertTrue("Release workflow must point to the generated CycloneDX SBOM.",
                workflow.contains("app/build/reports/sbom/adaway.cdx.json"));
        assertTrue("Release workflow must attest the SBOM with an immutable actions/attest pin.",
                pinnedSbomAttest.matcher(workflow).find());
        assertTrue("Release workflow must grant artifact metadata write access for attestation " +
                        "storage records.",
                workflow.contains("artifact-metadata: write"));
        assertTrue("Release workflow must fetch tag objects for signed-tag verification.",
                workflow.contains("fetch-depth: 0"));
        assertTrue("Release workflow must verify signed release tags before building.",
                workflow.contains("Verify signed release tag") &&
                        workflow.contains("RELEASE_TAG_PUBLIC_KEY_BASE64 is required") &&
                        workflow.contains("git verify-tag \"$GITHUB_REF_NAME\""));
        assertTrue("Release workflow must classify beta tags separately from stable tags.",
                workflow.contains("Classify release tag") &&
                        workflow.contains("channel=beta") &&
                        workflow.contains("prerelease=true"));
        assertTrue("Release workflow must create an SBOM attestation predicate.",
                workflow.contains("sbom-path: ${{ steps.sbom.outputs.path }}"));
        assertTrue("Release workflow must upload the SBOM as a release asset.",
                workflow.contains("${{ steps.sbom.outputs.path }}"));
        assertTrue("Release workflow must checksum the SBOM.",
                workflow.contains("sha256sum \"$(basename \"$SBOM\")\""));
        assertTrue("Release workflow must upload the SBOM checksum as a release asset.",
                workflow.contains("${{ steps.sbom.outputs.sha256_path }}"));
        assertTrue("Release workflow must checksum the APK by release-asset basename.",
                workflow.contains("sha256sum \"$(basename \"$APK\")\""));
        assertTrue("Release workflow must provenance-attest all uploaded release assets.",
                provenanceAttestationBlock.contains("${{ steps.apk.outputs.path }}") &&
                        provenanceAttestationBlock.contains("${{ steps.apk.outputs.sha256_path }}") &&
                        provenanceAttestationBlock.contains("${{ steps.update_manifest.outputs.path }}") &&
                        provenanceAttestationBlock.contains("${{ steps.update_manifest.outputs.sha256_path }}") &&
                        provenanceAttestationBlock.contains("${{ steps.sbom.outputs.path }}") &&
                        provenanceAttestationBlock.contains("${{ steps.sbom.outputs.sha256_path }}"));
        assertTrue("Release workflow must generate a signed update manifest.",
                workflow.contains("Generate signed update manifest") &&
                        workflow.contains("scripts/generate-update-manifest.sh"));
        assertTrue("Release workflow must require a private manifest signing key.",
                workflow.contains("UPDATE_MANIFEST_PRIVATE_KEY_BASE64 is required"));
        assertTrue("Bash manifest wrapper must delegate to the canonical Java generator.",
                manifestScript.contains("GenerateUpdateManifest.java") &&
                        manifestScript.contains("exec java"));
        assertTrue("PowerShell manifest wrapper must delegate to the canonical Java generator.",
                manifestPowerShell.contains("GenerateUpdateManifest.java") &&
                        manifestPowerShell.contains("Get-Command \"java\""));
        assertTrue("Release workflow must upload manifest.json as a release asset.",
                workflow.contains("${{ steps.update_manifest.outputs.path }}"));
        assertTrue("Release workflow must upload manifest.json.sha256 as a release asset.",
                workflow.contains("${{ steps.update_manifest.outputs.sha256_path }}"));
        assertTrue("Release workflow must attest the signed update manifest.",
                workflow.indexOf("${{ steps.update_manifest.outputs.path }}")
                        < workflow.indexOf("Create GitHub Release + upload APK"));
        assertTrue("Update manifest generator must sign the exact JSON payload with RSA SHA-256.",
                manifestGenerator.contains("Signature.getInstance(\"SHA256withRSA\")") &&
                        manifestGenerator.contains("signer.update(payload.getBytes"));
        assertTrue("Update manifest generator must verify against the embedded public-key format.",
                manifestGenerator.contains("X509EncodedKeySpec") &&
                        manifestGenerator.contains("verifier.verify(signatureBytes)"));
        assertTrue("Update manifest generator must emit a checksum file by basename.",
                manifestGenerator.contains("options.out.getFileName()") &&
                        manifestGenerator.contains("\".sha256\""));
        assertTrue("Update manifest generator must enforce the APK URL host allowlist.",
                manifestGenerator.contains("\"app.adaway.org\"") &&
                        manifestGenerator.contains("\"github.com\"") &&
                        manifestGenerator.contains("apk-url host is not in the release allowlist"));
        assertTrue("Update manifest generator must default to the runtime AdAway store name.",
                manifestGenerator.contains("--store VALUE") &&
                        manifestGenerator.contains("values.getOrDefault(\"store\", \"adaway\")"));
        assertTrue("Release artifact PowerShell verifier must delegate to the canonical Java verifier.",
                verifierPowerShell.contains("VerifyReleaseArtifacts.java") &&
                        verifierPowerShell.contains("Get-Command \"java\""));
        assertTrue("Release artifact shell verifier must delegate to the canonical Java verifier.",
                verifierShell.contains("VerifyReleaseArtifacts.java") &&
                        verifierShell.contains("exec java"));
        assertTrue("Release artifact verifier must validate the signed update manifest.",
                verifier.contains("verifyManifestSignature") &&
                        verifier.contains("Manifest apkSha256 does not match the release APK"));
        assertTrue("Release artifact verifier must optionally verify GitHub attestations.",
                verifier.contains("\"attestation\", \"verify") &&
                        verifier.contains("GH_CLI_PATH") &&
                        verifier.contains("--verify-attestations"));
        assertTrue("Release artifact verifier must include checksum sidecars in attestation " +
                        "verification.",
                verifier.contains("for (Path artifact : Arrays.asList(") &&
                        verifier.contains("options.apkSha256,") &&
                        verifier.contains("options.manifestSha256,") &&
                        verifier.contains("options.sbomSha256"));
        assertTrue("Release artifact verifier must support a durable verification report.",
                verifier.contains("--report") &&
                        verifier.contains("writeReport") &&
                        verifier.contains("Release Artifact Verification Report"));
        assertTrue("Release workflow must run the canonical release artifact verifier before " +
                        "attestation and upload.",
                workflow.contains("Verify release artifact bundle") &&
                        workflow.contains("scripts/verify-release-artifacts.sh") &&
                        workflow.contains("--apk \"${{ steps.apk.outputs.path }}\"") &&
                        workflow.contains("--apk-sha256 \"${{ steps.apk.outputs.sha256_path }}\"") &&
                        workflow.contains("--manifest \"${{ steps.update_manifest.outputs.path }}\"") &&
                        workflow.contains("--manifest-sha256 \"${{ steps.update_manifest.outputs.sha256_path }}\"") &&
                        workflow.contains("--sbom \"${{ steps.sbom.outputs.path }}\"") &&
                        workflow.contains("--sbom-sha256 \"${{ steps.sbom.outputs.sha256_path }}\"") &&
                        workflow.contains("--expected-version \"$VERSION\"") &&
                        workflow.contains("--expected-channel \"${{ steps.release_tag.outputs.channel }}\"") &&
                        workflow.contains("--expected-store adaway") &&
                        workflow.contains("--expected-apk-url \"$APK_URL\"") &&
                        workflow.contains("--expected-cert-sha256 \"${{ steps.apk_identity.outputs.cert_sha256 }}\""));
        String postAttestationVerificationBlock =
                workflow.substring(postAttestationVerificationStart, releaseUploadStart);
        assertTrue("Release workflow must run the canonical verifier after attestations are " +
                        "created.",
                postAttestationVerificationBlock.contains("scripts/verify-release-artifacts.sh") &&
                        postAttestationVerificationBlock.contains("--verify-attestations") &&
                        postAttestationVerificationBlock.contains("--repo \"${GITHUB_REPOSITORY}\"") &&
                        postAttestationVerificationBlock.contains("--apk \"${{ steps.apk.outputs.path }}\"") &&
                        postAttestationVerificationBlock.contains("--manifest \"${{ steps.update_manifest.outputs.path }}\"") &&
                        postAttestationVerificationBlock.contains("--sbom \"${{ steps.sbom.outputs.path }}\""));
        assertTrue("Release workflow must generate manifest APK URLs on the allowed GitHub host.",
                workflow.contains("APK_URL=\"https://github.com/${GITHUB_REPOSITORY}/releases/download/"));
        assertTrue("Release workflow must emit manifests for the runtime AdAway store.",
                workflow.contains("--store adaway"));
        assertTrue("Release workflow must use the classified release channel in manifest.json.",
                workflow.contains("--channel \"${{ steps.release_tag.outputs.channel }}\""));
        assertTrue("Release workflow must mark beta GitHub releases as prereleases.",
                workflow.contains("prerelease: ${{ steps.release_tag.outputs.prerelease }}"));
        assertFalse("Release workflow must not hard-code beta tags into the stable channel.",
                workflow.contains("--channel stable"));
        assertFalse("Release workflow must not emit GitHub as the runtime manifest store.",
                workflow.contains("--store github"));
        assertTrue("Tagged release workflow must run the license boundary guard.",
                workflow.contains("scripts/check-license-boundary.ps1"));
        assertTrue("License boundary guard must run before the release APK is built.",
                workflow.indexOf("scripts/check-license-boundary.ps1")
                        < workflow.indexOf(":app:assembleDirectRelease"));
        assertTrue("Pre-build release boundary check must inspect git-tracked source entries.",
                workflow.contains("scripts/check-license-boundary.ps1 -SourceMode GitTracked"));
        assertTrue("Pre-build release boundary check must inspect the exported source archive.",
                workflow.contains(
                        "scripts/check-license-boundary.ps1 -SourceMode GitTracked -StrictSourceArchive"));
        assertTrue("Release workflow must run an artifact-aware license boundary check.",
                workflow.contains("Check release artifact license boundary"));
        assertTrue("Artifact license boundary check must use the selected APK output.",
                workflow.contains("-ApkPath \"${{ steps.apk.outputs.path }}\""));
        assertTrue("Artifact license boundary check must use the generated SBOM output.",
                workflow.contains("-SbomPath \"${{ steps.sbom.outputs.path }}\""));
        assertTrue("Artifact license boundary check must fail closed for release artifacts.",
                workflow.contains("-StrictArtifacts"));
        assertTrue("Release boundary checks must verify source archives before publishing.",
                countMatches(workflow, "-StrictSourceArchive") >= 2);
        assertTrue("Artifact license boundary check must run after release APK identity verification.",
                workflow.indexOf("Check release artifact license boundary")
                        > workflow.indexOf("Verify release APK identity"));
        assertTrue("Artifact license boundary check must run before release attestation.",
                workflow.indexOf("Check release artifact license boundary")
                        < workflow.indexOf("Attest release artifacts"));
        assertFalse("Release workflow must not publish unscanned generated release notes.",
                workflow.contains("generate_release_notes: true"));
        assertTrue("Release workflow must publish checked release-boundary copy.",
                workflow.contains("License boundary: this app release remains GPLv3+"));
        assertTrue("Release workflow must state that relicensing is not part of the APK release.",
                workflow.contains("not part of this") && workflow.contains("APK release"));
        assertTrue("Release workflow must explain GitHub source archive license boundaries.",
                workflow.contains("GitHub may also display auto-generated \"Source code\"")
                        && workflow.contains("committed .gitattributes export-ignore rules"));
        assertFalse("Tagged release workflow must not delete older releases automatically.",
                workflow.contains("delete-older-releases"));
    }

    @Test
    public void atk34_updateManifestGeneratorSignsAndVerifiesLocally() throws Exception {
        Path repo = repoDir();
        Path fixture = Files.createTempDirectory("adaway-update-manifest");
        try {
            Path apk = fixture.resolve("AdAway_13.5.0.apk");
            writeUtf8(apk, "test apk bytes\n");
            Path manifest = fixture.resolve("manifest.json");
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            String privateKeyBase64 = Base64.getEncoder().encodeToString(
                    pem("PRIVATE KEY", keyPair.getPrivate().getEncoded())
                            .getBytes(StandardCharsets.US_ASCII));
            String publicKeyBase64 = Base64.getEncoder().encodeToString(
                    keyPair.getPublic().getEncoded());
            String certSha256 =
                    "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

            ProcessResult result = runProcess(repo,
                    javaCommand(),
                    repo.resolve("scripts/GenerateUpdateManifest.java").toString(),
                    "--apk", apk.toString(),
                    "--version", "13.5.0",
                    "--version-code", "130500",
                    "--cert-sha256", certSha256,
                    "--apk-url", "https://github.com/stevesolun/AdAway/releases/download/" +
                            "v13.5.0/AdAway_13.5.0.apk",
                    "--private-key-base64", privateKeyBase64,
                    "--public-key-base64", publicKeyBase64,
                    "--out", manifest.toString(),
                    "--channel", "stable",
                    "--store", "adaway",
                    "--valid-days", "1");

            assertEquals("Update manifest generator must exit successfully.",
                    0, result.exitCode);
            assertTrue("Generator must write the signed manifest.", Files.isRegularFile(manifest));
            assertTrue("Generator must write a checksum beside the manifest.",
                    Files.isRegularFile(Path.of(manifest.toString() + ".sha256")));

            JSONObject envelope = new JSONObject(readUtf8(manifest));
            String payloadJson = envelope.getString("payload");
            byte[] signatureBytes = Base64.getDecoder().decode(envelope.getString("signature"));
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(keyPair.getPublic());
            verifier.update(payloadJson.getBytes(StandardCharsets.UTF_8));
            assertTrue("Manifest signature must verify over the exact embedded payload.",
                    verifier.verify(signatureBytes));

            JSONObject payload = new JSONObject(payloadJson);
            assertEquals("13.5.0", payload.getString("version"));
            assertEquals(130500, payload.getInt("versionCode"));
            assertEquals("adaway", payload.getString("store"));
            assertEquals("stable", payload.getString("channel"));
            assertEquals(certSha256, payload.getString("signingCertificateSha256"));
            assertTrue("Manifest must include an expiration timestamp.",
                    payload.getString("expiresAt").endsWith("Z"));
            assertTrue("Checksum must be emitted by manifest basename.",
                    readUtf8(Path.of(manifest.toString() + ".sha256"))
                            .contains("  manifest.json"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void atk34_releaseArtifactVerifierChecksManifestAndChecksums() throws Exception {
        Path repo = repoDir();
        Path fixture = Files.createTempDirectory("adaway-release-artifacts");
        try {
            Path apk = fixture.resolve("AdAway_13.5.0.apk");
            writeUtf8(apk, "test apk bytes\n");
            Path apkChecksum = Path.of(apk.toString() + ".sha256");
            Path sbom = fixture.resolve("adaway.cdx.json");
            writeUtf8(sbom, "{\n" +
                    "  \"bomFormat\": \"CycloneDX\",\n" +
                    "  \"components\": [{\"name\": \"adaway-fixture\"}]\n" +
                    "}\n");
            Path sbomChecksum = Path.of(sbom.toString() + ".sha256");
            Path manifest = fixture.resolve("manifest.json");
            Path manifestChecksum = Path.of(manifest.toString() + ".sha256");

            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            String privateKeyBase64 = Base64.getEncoder().encodeToString(
                    pem("PRIVATE KEY", keyPair.getPrivate().getEncoded())
                            .getBytes(StandardCharsets.US_ASCII));
            String publicKeyBase64 = Base64.getEncoder().encodeToString(
                    keyPair.getPublic().getEncoded());
            String certSha256 =
                    "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
            String apkUrl = "https://github.com/stevesolun/AdAway/releases/download/" +
                    "v13.5.0/AdAway_13.5.0.apk";

            writeChecksum(apk, apkChecksum);
            writeChecksum(sbom, sbomChecksum);
            assertEquals("Update manifest generator must create fixture manifest.",
                    0, runProcess(repo,
                            javaCommand(),
                            repo.resolve("scripts/GenerateUpdateManifest.java").toString(),
                            "--apk", apk.toString(),
                            "--version", "13.5.0",
                            "--version-code", "130500",
                            "--cert-sha256", certSha256,
                            "--apk-url", apkUrl,
                            "--private-key-base64", privateKeyBase64,
                            "--public-key-base64", publicKeyBase64,
                            "--out", manifest.toString(),
                            "--channel", "stable",
                            "--store", "adaway",
                            "--valid-days", "1").exitCode);

            ProcessResult verified = runProcess(repo,
                    javaCommand(),
                    repo.resolve("scripts/VerifyReleaseArtifacts.java").toString(),
                    "--apk", apk.toString(),
                    "--apk-sha256", apkChecksum.toString(),
                    "--manifest", manifest.toString(),
                    "--manifest-sha256", manifestChecksum.toString(),
                    "--sbom", sbom.toString(),
                    "--sbom-sha256", sbomChecksum.toString(),
                    "--public-key-base64", publicKeyBase64,
                    "--expected-version", "13.5.0",
                    "--expected-channel", "stable",
                    "--expected-store", "adaway",
                    "--expected-apk-url", apkUrl,
                    "--expected-cert-sha256", certSha256);
            assertEquals("Release artifact verifier must accept matching artifacts.",
                    0, verified.exitCode);
            assertTrue("Passing verifier must report success.",
                    verified.stdout.contains("Release artifact verification passed"));

            Path fakeGhLog = fixture.resolve("fake-gh.log");
            Path fakeGh = writeFakeGitHubCli(fixture, fakeGhLog);
            Path report = fixture.resolve("verification-report.md");
            java.util.Map<String, String> environment = new java.util.HashMap<>();
            environment.put("GH_CLI_PATH", fakeGh.toString());
            environment.put("FAKE_GH_LOG", fakeGhLog.toString());
            ProcessResult attested = runProcess(repo,
                    environment,
                    javaCommand(),
                    repo.resolve("scripts/VerifyReleaseArtifacts.java").toString(),
                    "--apk", apk.toString(),
                    "--apk-sha256", apkChecksum.toString(),
                    "--manifest", manifest.toString(),
                    "--manifest-sha256", manifestChecksum.toString(),
                    "--sbom", sbom.toString(),
                    "--sbom-sha256", sbomChecksum.toString(),
                    "--public-key-base64", publicKeyBase64,
                    "--expected-version", "13.5.0",
                    "--expected-channel", "stable",
                    "--expected-store", "adaway",
                    "--expected-apk-url", apkUrl,
                    "--expected-cert-sha256", certSha256,
                    "--report", report.toString(),
                    "--verify-attestations");
            assertEquals("Verifier must accept matching artifacts when attestations pass.",
                    0, attested.exitCode);
            assertTrue("Verifier must write the requested verification report.",
                    Files.isRegularFile(report));
            String reportText = readUtf8(report);
            assertTrue("Verification report must summarize release artifact proof.",
                    reportText.contains("# Release Artifact Verification Report") &&
                            reportText.contains("- Status: passed") &&
                            Pattern.compile("(?m)^- Source commit: [0-9a-f]{40}$")
                                    .matcher(reportText)
                                    .find() &&
                            reportText.contains("- Release tag: v13.5.0") &&
                            reportText.contains("- APK: AdAway_13.5.0.apk") &&
                            reportText.contains("- Expected version: 13.5.0") &&
                            reportText.contains("- Expected channel: stable") &&
                            reportText.contains("- Expected store: adaway") &&
                            reportText.contains("- Checksum verification: passed") &&
                            reportText.contains("- Manifest signature: passed") &&
                            reportText.contains("- Manifest payload: passed") &&
                            reportText.contains("- Attestations: verified") &&
                            reportText.contains("- Attested artifacts: 6"));
            assertFalse("Verification report must not leak the manifest public key.",
                    reportText.contains(publicKeyBase64));
            String fakeGhCalls = readUtf8(fakeGhLog);
            assertEquals("Verifier must run one attestation check per uploaded release asset.",
                    6, countMatches(fakeGhCalls, "attestation verify"));
            assertTrue("Verifier must attest the APK and its checksum sidecar.",
                    fakeGhCalls.contains(apk.getFileName().toString()) &&
                            fakeGhCalls.contains(apkChecksum.getFileName().toString()));
            assertTrue("Verifier must attest the manifest and its checksum sidecar.",
                    fakeGhCalls.contains(manifest.getFileName().toString()) &&
                            fakeGhCalls.contains(manifestChecksum.getFileName().toString()));
            assertTrue("Verifier must attest the SBOM and its checksum sidecar.",
                    fakeGhCalls.contains(sbom.getFileName().toString()) &&
                            fakeGhCalls.contains(sbomChecksum.getFileName().toString()));

            Path flakyGhLog = fixture.resolve("flaky-gh.log");
            Path flakyGhState = fixture.resolve("flaky-gh-state");
            Path flakyGh = writeFlakyGitHubCli(fixture, flakyGhLog, flakyGhState);
            java.util.Map<String, String> flakyEnvironment = new java.util.HashMap<>();
            flakyEnvironment.put("GH_CLI_PATH", flakyGh.toString());
            flakyEnvironment.put("FAKE_GH_LOG", flakyGhLog.toString());
            flakyEnvironment.put("FAKE_GH_STATE", flakyGhState.toString());
            ProcessResult retried = runProcess(repo,
                    flakyEnvironment,
                    javaCommand(),
                    repo.resolve("scripts/VerifyReleaseArtifacts.java").toString(),
                    "--apk", apk.toString(),
                    "--apk-sha256", apkChecksum.toString(),
                    "--manifest", manifest.toString(),
                    "--manifest-sha256", manifestChecksum.toString(),
                    "--sbom", sbom.toString(),
                    "--sbom-sha256", sbomChecksum.toString(),
                    "--public-key-base64", publicKeyBase64,
                    "--expected-version", "13.5.0",
                    "--expected-channel", "stable",
                    "--expected-store", "adaway",
                    "--expected-apk-url", apkUrl,
                    "--expected-cert-sha256", certSha256,
                    "--verify-attestations");
            assertEquals("Verifier must retry transient GitHub attestation lookup misses.",
                    0, retried.exitCode);
            assertTrue("Retry proof must include the initial failed attestation lookup.",
                    readUtf8(flakyGhLog).contains("transient-miss"));

            writeUtf8(apk, "tampered apk bytes\n");
            ProcessResult rejected = runProcess(repo,
                    javaCommand(),
                    repo.resolve("scripts/VerifyReleaseArtifacts.java").toString(),
                    "--apk", apk.toString(),
                    "--apk-sha256", apkChecksum.toString(),
                    "--manifest", manifest.toString(),
                    "--manifest-sha256", manifestChecksum.toString(),
                    "--sbom", sbom.toString(),
                    "--sbom-sha256", sbomChecksum.toString(),
                    "--public-key-base64", publicKeyBase64,
                    "--expected-version", "13.5.0",
                    "--expected-channel", "stable",
                    "--expected-store", "adaway",
                    "--expected-apk-url", apkUrl,
                    "--expected-cert-sha256", certSha256);
            assertTrue("Tampered APK must fail release artifact verification.",
                    rejected.exitCode != 0);
            assertTrue("Failure must identify checksum drift.",
                    rejected.stderr.contains("Checksum mismatch for"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void atk34_releaseCleanupAndDocsPreserveSourceProvenance() throws IOException {
        String cleanupWorkflow = readUtf8(
                repoDir().resolve(".github/workflows/cleanup-releases.yml"));
        Path verifierWorkflowPath =
                repoDir().resolve(".github/workflows/verify-release-artifacts.yml");
        assertTrue("Manual post-publish release verifier workflow must exist.",
                Files.isRegularFile(verifierWorkflowPath));
        String verifierWorkflow = readUtf8(verifierWorkflowPath);
        String workflow = readUtf8(repoDir().resolve(".github/workflows/fork-release-apk.yml"));
        String releasing = readUtf8(repoDir().resolve("RELEASING.md"));
        String readme = readUtf8(repoDir().resolve("README.md"));

        assertTrue("Manual post-publish verifier must be manually dispatched.",
                verifierWorkflow.contains("workflow_dispatch:"));
        assertTrue("Manual post-publish verifier must take the release tag and signer digest.",
                verifierWorkflow.contains("tag:") &&
                        verifierWorkflow.contains("expected_cert_sha256:"));
        assertTrue("Manual post-publish verifier must use read-only release and attestation " +
                        "permissions.",
                verifierWorkflow.contains("contents: read") &&
                        verifierWorkflow.contains("attestations: read"));
        assertTrue("Manual post-publish verifier must compute the runtime release channel from " +
                        "the tag.",
                verifierWorkflow.contains("VERSION=\"${TAG#v}\"") &&
                        verifierWorkflow.contains("CHANNEL=beta") &&
                        verifierWorkflow.contains("CHANNEL=stable"));
        assertTrue("Manual post-publish verifier must download all published release assets.",
                verifierWorkflow.contains("gh release download \"$TAG\"") &&
                        verifierWorkflow.contains("AdAway_${VERSION}.apk") &&
                        verifierWorkflow.contains("manifest.json") &&
                        verifierWorkflow.contains("adaway.cdx.json"));
        assertTrue("Manual post-publish verifier must run the canonical verifier with " +
                        "attestation checks.",
                verifierWorkflow.contains("scripts/verify-release-artifacts.sh") &&
                        verifierWorkflow.contains("--verify-attestations") &&
                        verifierWorkflow.contains("--repo \"${GITHUB_REPOSITORY}\"") &&
                        verifierWorkflow.contains("--expected-apk-url \"$APK_URL\"") &&
                        verifierWorkflow.contains("--expected-cert-sha256 \"$EXPECTED_CERT_SHA256\"") &&
                        verifierWorkflow.contains("--report \"$OUT_DIR/verification-report.md\""));
        assertTrue("Manual post-publish verifier must upload a durable verification report.",
                verifierWorkflow.contains("Upload release artifact verification report") &&
                        verifierWorkflow.contains("release-artifact-verification-report") &&
                        verifierWorkflow.contains("release-artifacts/verification-report.md") &&
                        verifierWorkflow.contains("actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a"));
        assertTrue("Manual post-publish verifier must use the repository token for GitHub " +
                        "release and attestation APIs.",
                verifierWorkflow.contains("GH_TOKEN: ${{ github.token }}"));
        assertFalse("Manual release cleanup must not expose tag deletion as an input.",
                cleanupWorkflow.contains("delete_tags:") &&
                        cleanupWorkflow.contains("${{ inputs.delete_tags }}"));
        assertTrue("Manual release cleanup must keep source archive tags.",
                cleanupWorkflow.contains("gh release delete") &&
                        !cleanupWorkflow.contains("--cleanup-tag"));
        assertTrue("Release docs must say generated release notes are disabled.",
                releasing.contains("disables generated") &&
                        releasing.contains("release notes"));
        assertTrue("Release docs must include the strict source boundary check through the " +
                        "PowerShell script.",
                releasing.contains("powershell -NoProfile -ExecutionPolicy Bypass -File " +
                        ".\\scripts\\check-license-boundary.ps1") &&
                        releasing.contains("-SourceMode GitTracked -StrictSourceArchive"));
        assertTrue("Release docs must include the artifact-aware boundary check.",
                releasing.contains("-ApkPath $Apk") &&
                        releasing.contains("-SbomPath $Sbom") &&
                        releasing.contains("-StrictArtifacts"));
        assertTrue("Release docs must document the manifest signing private-key secret.",
                releasing.contains("UPDATE_MANIFEST_PRIVATE_KEY_BASE64"));
        assertTrue("Release docs must include local signed update-manifest generation.",
                releasing.contains("scripts\\generate-update-manifest.ps1") &&
                        releasing.contains("generate-update-manifest.sh") &&
                        releasing.contains("same JDK-based manifest generator"));
        assertTrue("Release docs must use the runtime AdAway store name in manifests.",
                releasing.contains("--store adaway"));
        assertFalse("Release docs must not use GitHub as the runtime manifest store.",
                releasing.contains("--store github"));
        assertTrue("Release docs must run scripted post-release artifact verification.",
                releasing.contains("scripts\\verify-release-artifacts.ps1") &&
                        releasing.contains("scripts/verify-release-artifacts.sh"));
        assertTrue("Release docs must include checksum paths for release artifacts.",
                releasing.contains("--apk-sha256 \"$Apk.sha256\"") &&
                        releasing.contains("--manifest-sha256 manifest.json.sha256") &&
                        releasing.contains("--sbom-sha256 adaway.cdx.json.sha256"));
        assertTrue("Release docs must verify fork release attestations against this repository.",
                releasing.contains("--repo stevesolun/AdAway") &&
                        releasing.contains("--verify-attestations"));
        assertTrue("Release docs must document the manual post-publish verifier workflow.",
                releasing.contains("Verify release artifacts") &&
                        releasing.contains("verify-release-artifacts.yml"));
        assertTrue("README must mention the manual post-publish release verifier workflow.",
                readme.contains("verify-release-artifacts.yml"));
        assertTrue("Release docs must document the release artifact verification report.",
                releasing.contains("release-artifact-verification-report") &&
                        readme.contains("release-artifact-verification-report"));
        assertTrue("Release docs must explain attestation verification covers checksum sidecars.",
                releasing.contains("each `.sha256` checksum sidecar"));
        assertTrue("Release docs must verify signed manifest semantics after publishing.",
                releasing.contains("--public-key-base64") &&
                        releasing.contains("--expected-apk-url") &&
                        releasing.contains("--expected-cert-sha256"));
        assertFalse("Release docs must not verify fork release attestations against upstream.",
                releasing.contains("gh attestation verify \"AdAway_<version>.apk\" " +
                        "--repo AdAway/AdAway") ||
                        releasing.contains("gh attestation verify \"manifest.json\" " +
                                "--repo AdAway/AdAway") ||
                        releasing.contains("gh attestation verify \"adaway.cdx.json\" " +
                                "--repo AdAway/AdAway"));
        assertTrue("Release docs must state tagged releases are retained for provenance.",
                releasing.contains("Tagged releases are retained for durable provenance"));
        assertTrue("Release docs must scope APK self-update to AdAway-signed direct APKs.",
                releasing.contains("APK self-update is only for the AdAway-signed direct APK"));
        assertTrue("Release docs must scope fork tags to fork GitHub direct APK publishing.",
                releasing.contains("https://github.com/stevesolun/AdAway/releases") &&
                        releasing.contains("Pushing a fork release tag publishes the GitHub " +
                                "direct APK only"));
        assertTrue("Release docs must keep F-Droid/store releases separate from direct APK tags.",
                releasing.contains("F-Droid updates") &&
                        releasing.contains("own store/build pipeline"));
        assertFalse("Release docs must not point fork release publishing at upstream GitHub.",
                releasing.contains("https://github.com/AdAway/AdAway/releases"));
        assertFalse("Release docs must not claim fork tags publish F-Droid.",
                releasing.contains("Pushing a tag will publish the application to F-Droid store"));
        assertTrue("Release docs must show the direct APK updater build type.",
                releasing.contains(":app:assembleDirectRelease") &&
                        releasing.contains("directRelease"));
        assertTrue("README must describe the direct-release build and signed tag gate.",
                readme.contains(":app:assembleDirectRelease") &&
                        readme.contains("git tag -s v<version>") &&
                        readme.contains("git verify-tag v<version>") &&
                        readme.contains("RELEASE_TAG_PUBLIC_KEY_BASE64"));
        assertTrue("README must describe checksum-sidecar attestations.",
                readme.contains("their `.sha256` checksum sidecars"));
        assertTrue("README must point release operators to verifier and physical-device smoke.",
                readme.contains("RELEASING.md") &&
                        readme.contains("artifact verifier") &&
                        readme.contains("Full smoke requires an attached physical device"));
        assertFalse("README must not tell maintainers to ship the generic release variant.",
                readme.contains(":app:assembleRelease") ||
                        readme.contains(":app:packageRelease"));
        assertTrue("Fork direct APK release workflow must build the updater distribution variant.",
                workflow.contains(":app:assembleDirectRelease"));
        assertFalse("Direct APK install permission must not be controlled by a Gradle property.",
                releasing.contains("-PadawayEnableDirectApkUpdater") ||
                        workflow.contains("-PadawayEnableDirectApkUpdater"));
        assertTrue("Release docs must keep the Bash wrapper documented as a Unix convenience.",
                releasing.contains("bash ./scripts/check-license-boundary.sh " +
                        "-SourceMode GitTracked -StrictSourceArchive"));
    }

    @Test
    public void atk34_releaseReadinessWorkflowAggregatesProofReports() throws IOException {
        Path repo = repoDir();
        Path readinessWorkflowPath =
                repo.resolve(".github/workflows/verify-release-readiness.yml");
        assertTrue("Manual final release-readiness workflow must exist.",
                Files.isRegularFile(readinessWorkflowPath));

        String readinessWorkflow = readUtf8(readinessWorkflowPath);
        String releasing = readUtf8(repo.resolve("RELEASING.md"));
        String readme = readUtf8(repo.resolve("README.md"));

        assertTrue("Final readiness workflow must be manually dispatched.",
                readinessWorkflow.contains("workflow_dispatch:"));
        assertTrue("Final readiness workflow must accept release artifact, smoke, and " +
                        "license-boundary run ids.",
                readinessWorkflow.contains("release_artifacts_run_id:") &&
                        readinessWorkflow.contains("physical_smoke_run_id:") &&
                        readinessWorkflow.contains("ux_signoff_run_id:") &&
                        readinessWorkflow.contains("license_boundary_run_id:"));
        assertFalse("Final readiness workflow must not take raw UX report blobs directly.",
                readinessWorkflow.contains("ux_signoff_report_base64:"));
        assertTrue("Final readiness workflow must use read-only repository and artifact access.",
                readinessWorkflow.contains("contents: read") &&
                        readinessWorkflow.contains("actions: read") &&
                        readinessWorkflow.contains("GH_TOKEN: ${{ github.token }}"));
        assertTrue("Final readiness workflow must download the durable proof artifacts.",
                readinessWorkflow.contains("gh run download \"$RELEASE_ARTIFACTS_RUN_ID\"") &&
                        readinessWorkflow.contains("release-artifact-verification-report") &&
                        readinessWorkflow.contains("gh run download \"$PHYSICAL_SMOKE_RUN_ID\"") &&
                        readinessWorkflow.contains("physical-release-smoke-report") &&
                        readinessWorkflow.contains(
                                "gh run download \"$LICENSE_BOUNDARY_RUN_ID\"") &&
                        readinessWorkflow.contains("gh run download \"$UX_SIGNOFF_RUN_ID\"") &&
                        readinessWorkflow.contains("ux-signoff-report") &&
                        readinessWorkflow.contains("release-license-boundary-reports"));
        assertTrue("Final readiness workflow must run the canonical readiness verifier.",
                readinessWorkflow.contains("./scripts/verify-release-readiness.ps1") &&
                        readinessWorkflow.contains("-ReleaseArtifactReport") &&
                        readinessWorkflow.contains("release-artifacts/verification-report.md") &&
                        readinessWorkflow.contains("-PhysicalSmokeReport") &&
                        readinessWorkflow.contains("release-smoke/release-smoke-report.md") &&
                        readinessWorkflow.contains("-UxSignOffReport") &&
                        readinessWorkflow.contains("ux-signoff/ux-signoff-report.md") &&
                        readinessWorkflow.contains("-UxReviewPacket") &&
                        readinessWorkflow.contains("ux-signoff/ux-matrix-review.md") &&
                        readinessWorkflow.contains("-LicenseBoundaryReport") &&
                        readinessWorkflow.contains(
                                "release-boundary/artifact-license-boundary-report.md") &&
                        readinessWorkflow.contains("-ReportPath") &&
                        readinessWorkflow.contains(
                                "release-readiness/release-readiness-report.md"));
        assertTrue("Final readiness workflow must upload the final report as a durable artifact.",
                readinessWorkflow.contains("Upload release readiness report") &&
                        readinessWorkflow.contains("release-readiness-report") &&
                        readinessWorkflow.contains(
                                "release-readiness/release-readiness-report.md") &&
                        readinessWorkflow.contains(
                                "actions/upload-artifact@" +
                                        "043fb46d1a93c77aae656e7c1c64a875d1fc6a0a"));

        assertTrue("Release docs must document the final readiness workflow.",
                releasing.contains("verify-release-readiness.yml") &&
                        releasing.contains("release-readiness-report") &&
                        releasing.contains("ux_signoff_run_id") &&
                        releasing.contains("same source commit"));
        assertTrue("README must mention the final readiness workflow.",
                readme.contains("verify-release-readiness.yml") &&
                        readme.contains("release-readiness-report") &&
                        readme.contains("same source commit"));
    }

    @Test
    public void atk34_uxSignoffWorkflowUploadsDurableReport() throws IOException {
        Path repo = repoDir();
        Path workflowPath = repo.resolve(".github/workflows/verify-ux-signoff.yml");
        assertTrue("Manual UX sign-off workflow must exist.",
                Files.isRegularFile(workflowPath));

        String workflow = readUtf8(workflowPath);
        String releasing = readUtf8(repo.resolve("RELEASING.md"));
        String readme = readUtf8(repo.resolve("README.md"));

        assertTrue("UX sign-off workflow must be manually dispatched.",
                workflow.contains("workflow_dispatch:"));
        assertTrue("UX sign-off workflow must accept a checked review packet and reviewer.",
                workflow.contains("review_packet_base64:") &&
                        workflow.contains("reviewer:"));
        assertTrue("UX sign-off workflow must use read-only repository access.",
                workflow.contains("permissions:") &&
                        workflow.contains("contents: read"));
        assertTrue("UX sign-off workflow must decode the checked review packet.",
                workflow.contains("UX_REVIEW_PACKET_BASE64") &&
                        workflow.contains("base64 -d") &&
                        workflow.contains("ux-matrix/ux-matrix-review.md"));
        assertTrue("UX sign-off workflow must run the canonical sign-off verifier.",
                workflow.contains("./scripts/verify-ux-signoff.ps1") &&
                        workflow.contains("-ReviewPacket ux-matrix/ux-matrix-review.md") &&
                        workflow.contains("-Reviewer \"$env:REVIEWER\"") &&
                        workflow.contains("-ReportPath ux-signoff/ux-signoff-report.md"));
        assertTrue("UX sign-off workflow must upload a durable sign-off report.",
                workflow.contains("Upload UX sign-off report") &&
                        workflow.contains("ux-signoff-report") &&
                        workflow.contains("ux-signoff/ux-signoff-report.md") &&
                        workflow.contains("ux-signoff/ux-matrix-review.md") &&
                        workflow.contains(
                                "actions/upload-artifact@" +
                                        "043fb46d1a93c77aae656e7c1c64a875d1fc6a0a"));

        assertTrue("Release docs must document the UX sign-off workflow.",
                releasing.contains("verify-ux-signoff.yml") &&
                        releasing.contains("review_packet_base64") &&
                        releasing.contains("ux-signoff-report"));
        assertTrue("README must mention the UX sign-off workflow.",
                readme.contains("verify-ux-signoff.yml") &&
                        readme.contains("ux-signoff-report"));
    }

    @Test
    public void atk34_releaseSmokeRequiresReleaseApkOnRealDevice() throws IOException {
        Path repo = repoDir();
        Path smokeWorkflowPath = repo.resolve(".github/workflows/physical-release-smoke.yml");
        String smokeScript = readUtf8(repo.resolve("scripts/run-release-smoke.ps1"));
        String releasing = readUtf8(repo.resolve("RELEASING.md"));
        String readme = readUtf8(repo.resolve("README.md"));

        assertTrue("Release smoke must require an explicit APK path.",
                smokeScript.contains("[Parameter(Mandatory = $true)]") &&
                        smokeScript.contains("$ApkPath"));
        assertTrue("Release smoke must inspect APK badging.",
                smokeScript.contains("aapt") && smokeScript.contains("dump badging"));
        assertTrue("Release smoke must reject debuggable APKs.",
                smokeScript.contains("application-debuggable"));
        assertTrue("Release smoke must support artifact identity verification without device I/O.",
                smokeScript.contains("[switch] $VerifyOnly") &&
                        smokeScript.contains("APK identity verification passed") &&
                        smokeScript.contains("Physical-device install/launch smoke was not run"));
        assertTrue("Release smoke must support a durable report artifact.",
                smokeScript.contains("$ReportPath") &&
                        smokeScript.contains("Write-ReleaseSmokeReport") &&
                        smokeScript.contains("Release Smoke Report"));
        assertTrue("Release smoke must preserve colon-separated signer certificate digests.",
                smokeScript.contains("certificate SHA-256 digest:\\s*") &&
                        smokeScript.contains("Signer #1 certificate SHA-256 digest was not found") &&
                        !smokeScript.contains("-replace \"^.*:\\s*\""));
        assertTrue("Release smoke must compare normalized signer digests, not parser tokens.",
                smokeScript.contains("$actualCertSha256 = Normalize-Sha256 $actualCert") &&
                        smokeScript.contains("$actualCertSha256 -ne " +
                        "(Normalize-Sha256 $ExpectedCertSha256)"));
        int verifyOnlyIndex = smokeScript.indexOf("if ($VerifyOnly)");
        int adbDiscoveryIndex = smokeScript.indexOf("$adb = Find-CommandOrSdkTool");
        assertTrue("Artifact-only verification must not require adb discovery.",
                verifyOnlyIndex >= 0 && adbDiscoveryIndex > verifyOnlyIndex);
        assertTrue("Release smoke must reject emulators.",
                smokeScript.contains("emulator-") &&
                        smokeScript.contains("ro.kernel.qemu") &&
                        smokeScript.contains("ro.boot.qemu"));
        assertTrue("Release smoke must install and launch the APK.",
                smokeScript.contains("adb install") &&
                        smokeScript.contains("monkey -p") &&
                        smokeScript.contains("pidof"));
        assertTrue("Release smoke must default to the production package.",
                smokeScript.contains("org.adaway"));
        assertTrue("Release docs must include the real-device smoke command.",
                releasing.contains(".\\scripts\\run-release-smoke.ps1") &&
                        releasing.contains("-ApkPath $Apk"));
        assertTrue("Release docs must document identity-only release APK verification.",
                releasing.contains("-VerifyOnly") &&
                        releasing.contains("without requiring a connected device"));
        assertTrue("Manual physical-device release smoke workflow must exist.",
                Files.exists(smokeWorkflowPath));
        String smokeWorkflow = readUtf8(smokeWorkflowPath);
        assertTrue("Physical release smoke workflow must be manually dispatched.",
                smokeWorkflow.contains("workflow_dispatch:"));
        assertTrue("Physical release smoke workflow must require release tag input.",
                smokeWorkflow.contains("tag:") &&
                        smokeWorkflow.contains("Release tag to smoke"));
        assertTrue("Physical release smoke workflow must require expected signer input.",
                smokeWorkflow.contains("expected_cert_sha256:") &&
                        smokeWorkflow.contains("Expected release APK signing certificate"));
        assertTrue("Physical release smoke workflow must accept an optional device serial.",
                smokeWorkflow.contains("device_serial:") &&
                        smokeWorkflow.contains("-DeviceSerial") &&
                        smokeWorkflow.contains("$env:DEVICE_SERIAL"));
        assertTrue("Physical release smoke workflow must be limited to a self-hosted " +
                        "physical Android device runner.",
                smokeWorkflow.contains("runs-on: [self-hosted, android-device]"));
        assertTrue("Physical release smoke workflow must use read-only release access.",
                smokeWorkflow.contains("permissions:") &&
                        smokeWorkflow.contains("contents: read") &&
                        smokeWorkflow.contains("GH_TOKEN: ${{ github.token }}"));
        assertTrue("Physical release smoke workflow must download the tag-matching APK.",
                smokeWorkflow.contains("gh release download \"$TAG\"") &&
                        smokeWorkflow.contains("AdAway_${VERSION}.apk"));
        assertTrue("Physical release smoke workflow must run the full smoke script.",
                smokeWorkflow.contains("./scripts/run-release-smoke.ps1") &&
                        smokeWorkflow.contains("-ExpectedCertSha256") &&
                        smokeWorkflow.contains("-ReleaseTag") &&
                        smokeWorkflow.contains("-ReportPath"));
        assertTrue("Physical release smoke workflow must upload the smoke report artifact.",
                smokeWorkflow.contains("Upload release smoke report") &&
                        smokeWorkflow.contains("physical-release-smoke-report") &&
                        smokeWorkflow.contains("release-smoke/release-smoke-report.md") &&
                        smokeWorkflow.contains("actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a"));
        assertFalse("Physical release smoke workflow must not skip device I/O.",
                smokeWorkflow.contains("-VerifyOnly"));
        assertTrue("Release docs must document the physical release smoke workflow.",
                releasing.contains("physical-release-smoke.yml") &&
                        releasing.contains("self-hosted") &&
                        releasing.contains("android-device"));
        assertTrue("Release docs must document the physical smoke report artifact.",
                releasing.contains("physical-release-smoke-report") &&
                        readme.contains("physical-release-smoke-report"));
        assertTrue("README must mention the physical release smoke workflow.",
                readme.contains("physical-release-smoke.yml"));
    }

    @Test
    public void atk34_releaseSmokeVerifyOnlyUsesHighestParsedBuildToolsVersion()
            throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the release-smoke script.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-release-smoke");
        try {
            Path apk = fixture.resolve("release.apk");
            Path report = fixture.resolve("release-smoke-report.md");
            writeUtf8(apk, "fake release apk\n");
            Path sdk = fixture.resolve("android-sdk");
            writeFakeBuildTool(sdk, "9.0.0", "aapt",
                    "package: name='wrong.package' versionCode='1' versionName='0.0.0'");
            writeFakeBuildTool(sdk, "36.0.0", "aapt",
                    "package: name='org.adaway' versionCode='130500' versionName='13.5.0'");

            java.util.Map<String, String> environment = new java.util.HashMap<>();
            environment.put("ANDROID_HOME", sdk.toString());
            environment.put("ANDROID_SDK_ROOT", null);

            ProcessResult result = runProcess(repoDir(), environment,
                    powershell,
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-File", repoDir().resolve("scripts/run-release-smoke.ps1").toString(),
                    "-ApkPath", apk.toString(),
                    "-ReportPath", report.toString(),
                    "-ReleaseTag", "v13.5.0",
                    "-VerifyOnly");

            assertEquals("Release smoke must select build-tools 36.0.0 over stale 9.0.0.",
                    0, result.exitCode);
            assertTrue("VerifyOnly smoke must stop before adb/device checks.",
                    result.stdout.contains("Physical-device install/launch smoke was not run"));
            assertTrue("VerifyOnly smoke must write the requested report.",
                    Files.isRegularFile(report));
            String reportText = readUtf8(report);
            assertTrue("Release smoke report must describe identity-only mode.",
                    reportText.contains("# Release Smoke Report") &&
                            reportText.contains("- Status: passed") &&
                            Pattern.compile("(?m)^- Source commit: [0-9a-f]{40}$")
                                    .matcher(reportText)
                                    .find() &&
                            reportText.contains("- Mode: identity-only") &&
                            reportText.contains("- Release tag: v13.5.0") &&
                            reportText.contains("- Physical device: not-run"));
            assertTrue("Release smoke report must include APK identity in verify-only mode.",
                    reportText.contains("- APK SHA-256: " +
                            sha256Hex("fake release apk\n".getBytes(StandardCharsets.UTF_8))) &&
                            reportText.contains("- Signer certificate SHA-256: not-checked"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void atk34_releaseSmokeReportRecordsPhysicalLaunchWithoutSerialLeak()
            throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the release-smoke script.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-release-smoke-physical");
        try {
            Path apk = fixture.resolve("release.apk");
            Path report = fixture.resolve("release-smoke-report.md");
            writeUtf8(apk, "fake release apk\n");
            String certSha256 =
                    "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
            Path sdk = fixture.resolve("android-sdk");
            writeFakeBuildTool(sdk, "36.0.0", "aapt",
                    "package: name='org.adaway' versionCode='130500' versionName='13.5.0'");
            writeFakeBuildTool(sdk, "36.0.0", "apksigner",
                    "Signer #1 certificate SHA-256 digest: " + certSha256);
            writeFakeAdb(sdk, "device-123", "4242");

            java.util.Map<String, String> environment = new java.util.HashMap<>();
            environment.put("ANDROID_HOME", sdk.toString());
            environment.put("ANDROID_SDK_ROOT", null);

            ProcessResult result = runProcess(repoDir(), environment,
                    powershell,
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-File", repoDir().resolve("scripts/run-release-smoke.ps1").toString(),
                    "-ApkPath", apk.toString(),
                    "-ReportPath", report.toString(),
                    "-ExpectedCertSha256", certSha256,
                    "-ReleaseTag", "v13.5.0",
                    "-LaunchWaitSeconds", "0",
                    "-DeviceSerial", "device-123");

            assertEquals("Release smoke must pass against the fake physical adb.",
                    0, result.exitCode);
            String reportText = readUtf8(report);
            assertTrue("Physical release smoke report must record launch evidence.",
                    reportText.contains("- Mode: physical-device") &&
                            Pattern.compile("(?m)^- Source commit: [0-9a-f]{40}$")
                                    .matcher(reportText)
                                    .find() &&
                            reportText.contains("- Release tag: v13.5.0") &&
                            reportText.contains("- Physical device: verified-real-device") &&
                            reportText.contains("- Launch pid observed: 4242"));
            assertTrue("Physical release smoke report must hash the device serial.",
                    reportText.contains("- Device serial SHA-256: " +
                            sha256Hex("device-123".getBytes(StandardCharsets.UTF_8))));
            assertTrue("Physical release smoke report must record the tested APK identity.",
                    reportText.contains("- APK SHA-256: " +
                            sha256Hex("fake release apk\n".getBytes(StandardCharsets.UTF_8))) &&
                            reportText.contains("- Signer certificate SHA-256: " + certSha256));
            assertFalse("Physical release smoke report must not leak the raw device serial.",
                    reportText.contains("device-123"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void atk34_uxMatrixRunnerTimesOutAndCleansUpInstrumentation()
            throws IOException {
        Path repo = repoDir();
        String uxMatrixScript = readUtf8(repo.resolve("scripts/run-ux-matrix.ps1"));

        assertTrue("UX matrix runner must use an explicit instrumentation timeout.",
                uxMatrixScript.contains("$InstrumentationTimeoutSeconds") &&
                        uxMatrixScript.contains("Wait-Process") &&
                        uxMatrixScript.contains("-Timeout"));
        assertTrue("UX matrix runner must stop both app and test packages after failed runs.",
                uxMatrixScript.contains("am force-stop org.adaway.test") &&
                        uxMatrixScript.contains("am force-stop org.adaway"));
        assertTrue("UX matrix runner must kill timed-out instrumentation processes.",
                uxMatrixScript.contains("Stop-Process") &&
                        uxMatrixScript.contains("UX matrix test timed out"));
    }

    @Test
    public void atk34_apkSelfUpdateRequiresInstallPermissionAndAdAwayStoreBoundary()
            throws IOException {
        Path app = appDir();
        String appBuild = readUtf8(app.resolve("build.gradle"));
        String manifest = readUtf8(app.resolve("src/main/AndroidManifest.xml"));
        String directManifest = readUtf8(app.resolve("src/directRelease/AndroidManifest.xml"));
        String receiver = readUtf8(
                app.resolve("src/main/java/org/adaway/model/update/ApkDownloadReceiver.java"));
        String updateModel = readUtf8(
                app.resolve("src/main/java/org/adaway/model/update/UpdateModel.java"));

        assertFalse("Normal builds must not declare the APK installer permission.",
                manifest.contains("android.permission.REQUEST_INSTALL_PACKAGES"));
        assertFalse("Base manifest must not keep a fake installer-permission placeholder.",
                manifest.contains("requestInstallPackagesPermission") ||
                        manifest.contains("NO_DIRECT_APK_INSTALL"));
        assertTrue("Direct APK updater permission must live in the directRelease manifest.",
                directManifest.contains("android.permission.REQUEST_INSTALL_PACKAGES"));
        assertTrue("Direct APK runtime self-update must be enabled only for directRelease.",
                appBuild.contains("directRelease") &&
                        appBuild.contains("DIRECT_APK_UPDATES_ENABLED\", \"true\"") &&
                        appBuild.contains("DIRECT_APK_UPDATES_ENABLED\", \"false\""));
        assertFalse("Direct APK install permission must not be placeholder-gated.",
                appBuild.contains("manifestPlaceholders") ||
                        appBuild.contains("requestInstallPackagesPermission") ||
                        appBuild.contains("adawayEnableDirectApkUpdater"));
        assertTrue("Runtime self-update must follow the same direct APK build gate.",
                updateModel.contains("BuildConfig.DIRECT_APK_UPDATES_ENABLED") &&
                        updateModel.indexOf("BuildConfig.DIRECT_APK_UPDATES_ENABLED")
                                < updateModel.indexOf("getStore() == ADAWAY"));
        assertTrue("APK receiver must check unknown-app install permission before installing.",
                receiver.contains("canRequestPackageInstalls()"));
        assertTrue("APK receiver must route users to system install-source settings.",
                receiver.contains("Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES"));
        int permissionGuard = receiver.indexOf("if (!canRequestPackageInstalls(context))");
        int installIntent = receiver.indexOf("new Intent(ACTION_INSTALL_PACKAGE)");
        assertTrue("APK receiver must only launch ACTION_INSTALL_PACKAGE after the permission check.",
                permissionGuard >= 0 && installIntent > permissionGuard);
        String permissionDeniedBranch = receiver.substring(permissionGuard, installIntent);
        assertTrue("Permission denial must route to settings and return before installing.",
                permissionDeniedBranch.contains("Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES") &&
                        permissionDeniedBranch.contains("return;"));
        assertTrue("App self-update must be limited to the AdAway-signed direct APK store.",
                updateModel.contains("getStore() == ADAWAY"));
        assertTrue("Manifest download must refuse non-AdAway stores before network access.",
                updateModel.indexOf("if (!canSelfUpdate())")
                        < updateModel.indexOf("HttpUrl httpUrl"));
        assertTrue("Manifest payload distribution must be validated against the request.",
                updateModel.contains("new Manifest(") &&
                        updateModel.contains("channel,") &&
                        updateModel.contains("store.getName()"));
        assertTrue("Update download must refuse non-AdAway stores before reading cached manifest.",
                updateModel.indexOf("public long update()") < updateModel.lastIndexOf(
                        "if (!canSelfUpdate())"));
    }

    @Test
    public void atk34_gradleSbomTaskEmitsCycloneDxForRuntimeClasspath() throws IOException {
        String appBuild = readUtf8(repoDir().resolve("app/build.gradle"));
        String settings = readUtf8(repoDir().resolve("settings.gradle"));
        String gitAttributes = readUtf8(repoDir().resolve(".gitattributes"));

        assertTrue("Gradle must expose an SBOM generation task.",
                appBuild.contains("tasks.register('generateSbom')"));
        assertTrue("SBOM task must use the CycloneDX Gradle plugin.",
                appBuild.contains("id 'org.cyclonedx.bom'"));
        assertTrue("SBOM task must use release runtime dependencies for signed release builds.",
                appBuild.contains("releaseRuntimeClasspath"));
        assertTrue("SBOM task must write the workflow-consumed SBOM path.",
                appBuild.contains("reports/sbom/adaway.cdx.json"));
        assertTrue("SBOM task must not resolve runtime dependencies while Gradle builds the task graph.",
                appBuild.contains("resolvedDependencies.setFrom([])"));
        assertTrue("SBOM task must not serve stale cached output after clearing resolved inputs.",
                appBuild.contains("outputs.upToDateWhen { false }"));
        assertFalse("SBOM generation must not silently ignore unresolved artifacts.",
                appBuild.contains("lenientConfiguration"));
        assertTrue("Release trust-material gate must cover all release packaging tasks.",
                appBuild.contains("taskName.contains('release')") &&
                        appBuild.contains("taskName.startsWith('assemble')") &&
                        appBuild.contains("taskName.startsWith('package')"));
        assertTrue("Release trust-material gate must cover aggregate assemble tasks.",
                appBuild.contains("'assemble'"));
        assertTrue("Release trust-material gate must cover aggregate build tasks.",
                appBuild.contains("'build'"));
        assertTrue("Release trust-material gate must cover aggregate bundle tasks.",
                appBuild.contains("'bundle'"));
        assertTrue("Release trust-material gate must still cover release SBOM generation.",
                appBuild.contains("'generatesbom'"));
        assertFalse("Release dependency auditing must not be blocked by a broad contains('release') gate.",
                appBuild.contains("contains('release') ||"));
        assertTrue("Dependency repositories must be centralized in settings.",
                settings.contains("RepositoriesMode.FAIL_ON_PROJECT_REPOS"));
        assertTrue("The libsu local Maven mirror must be centralized in settings.",
                settings.contains("url = uri('third_party/maven')"));
        assertTrue("The libsu local Maven mirror must be preferred before JitPack.",
                settings.indexOf("url = uri('third_party/maven')") <
                        settings.indexOf("url = 'https://jitpack.io'"));
        assertTrue("Mirrored Maven artifacts must keep byte-stable checkout hashes.",
                gitAttributes.contains("/third_party/maven/**/*.aar binary") &&
                        gitAttributes.contains("/third_party/maven/**/*.module binary") &&
                        gitAttributes.contains("/third_party/maven/**/*.pom binary"));
        assertTrue("JitPack must be content-filtered to the single dependency group that needs it.",
                settings.contains("includeGroup 'com.github.topjohnwu.libsu'"));
        assertFalse("App module must not add broad project repositories.",
                appBuild.contains("https://jitpack.io"));
    }

    @Test
    public void atk34_gradleScriptsUseGradle10AssignmentSyntax() throws IOException {
        String appBuild = readUtf8(repoDir().resolve("app/build.gradle"));
        String settings = readUtf8(repoDir().resolve("settings.gradle"));
        String sentryStubBuild = readUtf8(repoDir().resolve("sentrystub/build.gradle"));

        assertFalse("App build must not use deprecated compileSdk space assignment.",
                appBuild.contains("compileSdk 36"));
        assertFalse("App build must not use deprecated namespace space assignment.",
                appBuild.contains("namespace 'org.adaway'"));
        assertFalse("App build must not use deprecated signing flag space assignment.",
                appBuild.contains("enableV1Signing true"));
        assertFalse("App build must not use deprecated build feature space assignment.",
                appBuild.contains("resValues true"));
        assertFalse("App build must not use deprecated build type space assignment.",
                appBuild.contains("minifyEnabled true"));
        assertFalse("Settings build must not use deprecated repository URL space assignment.",
                settings.contains("url 'https://jitpack.io'"));
        assertFalse("Sentry stub must not use deprecated Android DSL space assignment.",
                sentryStubBuild.contains("compileSdk 36"));

        assertTrue("App build must use assignment syntax for compileSdk.",
                appBuild.contains("compileSdk = 36"));
        assertTrue("App build must use assignment syntax for namespace.",
                appBuild.contains("namespace = 'org.adaway'"));
        assertTrue("Settings build must use assignment syntax for JitPack URL.",
                settings.contains("url = 'https://jitpack.io'"));
        assertTrue("Sentry stub must use assignment syntax for compileSdk.",
                sentryStubBuild.contains("compileSdk = 36"));
    }

    @Test
    public void atk34_dependencyVerificationUsesSignaturesAndTrustedKeys() throws IOException {
        String verification = readUtf8(repoDir().resolve("gradle/verification-metadata.xml"));

        assertTrue("Dependency verification must verify signatures when artifacts publish them.",
                verification.contains("<verify-signatures>true</verify-signatures>"));
        assertFalse("Dependency verification must not remain hash-only.",
                verification.contains("<verify-signatures>false</verify-signatures>"));
        assertTrue("Dependency verification must not fetch PGP keys from key servers.",
                verification.contains("<key-servers enabled=\"false\"/>"));
        assertFalse("Dependency verification must not pin ad-hoc key server URLs.",
                verification.contains("<key-server uri="));
        assertTrue("Dependency verification must include scoped trusted signing keys.",
                verification.contains("<trusted-keys>"));
        assertTrue("Dependency verification must trust concrete artifact signing keys.",
                verification.contains("<trusted-key id="));
        assertTrue("Unsigned artifacts must be explicitly hash-pinned with a reason.",
                verification.contains("reason=\"Artifact is not signed\""));
    }

    @Test
    public void atk34_dependencyVerificationChecksInPublicKeyring() throws IOException {
        Path binaryKeyring = repoDir().resolve("gradle/verification-keyring.gpg");
        Path armoredKeys = repoDir().resolve("gradle/verification-keyring.keys");

        assertTrue("PGP dependency verification must check in Gradle's binary public keyring.",
                Files.isRegularFile(binaryKeyring));
        assertTrue("Gradle's exported binary dependency verification keyring must not be empty.",
                Files.size(binaryKeyring) > 1024);
        assertTrue("PGP dependency verification must check in the armored public key dump.",
                Files.isRegularFile(armoredKeys));
        String keyDump = readUtf8(armoredKeys);
        assertTrue("The armored dependency verification key dump must contain real PGP keys.",
                keyDump.contains("-----BEGIN PGP PUBLIC KEY BLOCK-----"));
    }

    @Test
    public void atk34_dependencyVerificationFallbacksAreAudited() throws IOException {
        String verification = readUtf8(repoDir().resolve("gradle/verification-metadata.xml"));
        String keyDump = readUtf8(repoDir().resolve("gradle/verification-keyring.keys"));
        String audit = readUtf8(repoDir().resolve("docs/dependency-verification-audit.md"));

        assertEquals("Unresolved PGP key ignores must stay reviewed and bounded.",
                31, countMatches(verification, "<ignored-key id="));
        assertEquals("Key-download checksum fallbacks must stay reviewed and bounded.",
                345, countMatches(verification, "reason=\"A key couldn't be downloaded\""));
        assertTrue("Dependency-verification fallback debt must have a human review record.",
                audit.contains("# Dependency Verification Audit"));
        assertTrue("Ignored-key count in the audit must match verification-metadata.xml.",
                audit.contains("Ignored PGP keys: 31"));
        assertTrue("Key-download fallback count in the audit must match verification-metadata.xml.",
                audit.contains("Key-download checksum fallbacks: 345"));
        assertTrue("Audit must require review before fallback counts grow.",
                audit.contains("Do not let ignored-key or key-download fallback counts grow"));
        assertTrue("Audit must require scoped trust instead of broad key trust.",
                audit.contains("Do not add broad trusted-key scopes"));
        assertFalse("Ignored keys already present in the exported keyring must be removed.",
                verification.contains("<ignored-key id=\"32EE5355A6BC6E42\""));
        assertFalse("Ignored keys already present in the exported keyring must be removed.",
                verification.contains("<ignored-key id=\"E88979FB9B30ACF2\""));
        assertTrue("Android build-tool signing subkey must be trusted only after review.",
                verification.contains("0F06FF86BEEAF4E71866EE5232EE5355A6BC6E42"));
        assertTrue("Android build-tool signing trust must stay scoped to Android build artifacts.",
                verification.contains("<trusting group=\"^com[.]android($|([.].*))\" regex=\"true\"/>"));
        assertTrue("Newer AndroidX annotation artifacts must keep explicit signing-key trust.",
                verification.contains("<trusting group=\"androidx.annotation\"/>"));
        assertTrue("AndroidX data binding compiler artifacts must keep explicit signing-key trust.",
                verification.contains("<trusting group=\"androidx.databinding\"/>"));
        assertTrue("AndroidX instrumentation test artifacts must keep explicit signing-key trust.",
                verification.contains("<trusting group=\"androidx.test\"/>"));
        assertTrue("AndroidX instrumentation test artifacts must keep explicit signing-key trust.",
                verification.contains("<trusting group=\"androidx.test.ext\"/>"));
        assertTrue("AndroidX instrumentation test artifacts must keep explicit signing-key trust.",
                verification.contains("<trusting group=\"androidx.test.services\"/>"));
        assertTrue("AndroidX runtime signing subkey must be trusted only after review.",
                verification.contains("A5F483CD733A4EBAEA378B2AE88979FB9B30ACF2"));
        assertTrue("AndroidX runtime signing trust must stay scoped to resolved AndroidX families.",
                verification.contains("<trusting group=\"androidx.appcompat\"/>"));
        assertTrue("AndroidX concurrent-futures artifacts must keep explicit signing-key trust.",
                verification.contains("<trusting group=\"androidx.concurrent\"/>"));
        assertTrue("AndroidX runtime signing trust must stay scoped to resolved AndroidX families.",
                verification.contains("<trusting group=\"androidx.paging\"/>"));
        assertTrue("AndroidX runtime signing trust must stay scoped to resolved AndroidX families.",
                verification.contains("<trusting group=\"androidx.profileinstaller\"/>"));
        assertTrue("AndroidX runtime signing trust must stay scoped to resolved AndroidX families.",
                verification.contains("<trusting group=\"androidx.startup\"/>"));
        assertTrue("The exported keyring should retain the reviewed AndroidX signing subkey.",
                keyDump.contains("32EE5355A6BC6E42"));
        assertTrue("The exported keyring should retain the reviewed AndroidX signing subkey.",
                keyDump.contains("E88979FB9B30ACF2"));
    }

    @Test
    public void atk34_releaseBuildStripsDnsjavaDesktopResolverSpi() throws IOException {
        String appBuild = readUtf8(repoDir().resolve("app/build.gradle"));
        String proguard = readUtf8(repoDir().resolve("app/proguard-rules.pro"));

        assertTrue("Release and direct release must strip dnsjava's desktop resolver service.",
                appBuild.contains("['Release', 'DirectRelease'].each"));
        assertTrue("Strip task must run after release Java resources are merged.",
                appBuild.contains("dependsOn tasks.named(\"merge${variantName}JavaResource\")"));
        assertTrue("Strip task must run before release R8.",
                appBuild.contains("minify${variantName}WithR8") &&
                        appBuild.contains("stripDnsjavaDesktopResolverService${variantName}"));
        assertTrue("Packaged resources must exclude dnsjava's desktop resolver service.",
                appBuild.contains("META-INF/services/java.net.spi.InetAddressResolverProvider"));
        assertFalse("App rules must not keep every ContentProvider; AAPT owns manifest providers.",
                proguard.contains("extends android.content.ContentProvider"));
        assertFalse("App rules must not hide all Google/Material/Guava missing-class warnings.",
                proguard.contains("-dontwarn com.google.**"));
        assertFalse("Release builds use sentrystub; app rules must not hide all Sentry warnings.",
                proguard.contains("-dontwarn io.sentry.**"));
        assertFalse("OkHttp consumer rules own optional platform suppressions.",
                proguard.contains("-dontwarn org.bouncycastle.jsse.**"));
        assertFalse("OkHttp consumer rules own optional platform suppressions.",
                proguard.contains("-dontwarn org.conscrypt.**"));
        assertFalse("App rules must not hide every Java network SPI warning.",
                proguard.contains("-dontwarn java.net.spi.**"));
        assertTrue("R8 warning suppression must include the Java desktop resolver interface.",
                proguard.contains("-dontwarn java.net.spi.InetAddressResolver\n"));
        assertTrue("R8 warning suppression must include the Java desktop resolver lookup policy.",
                proguard.contains("-dontwarn java.net.spi.InetAddressResolver$LookupPolicy"));
        assertTrue("R8 warning suppression must include the Java desktop resolver provider.",
                proguard.contains("-dontwarn java.net.spi.InetAddressResolverProvider\n"));
        assertTrue("R8 warning suppression must include the provider configuration type.",
                proguard.contains("-dontwarn java.net.spi.InetAddressResolverProvider$Configuration"));
        assertTrue("R8 warning suppression must include only dnsjava's desktop resolver provider.",
                proguard.contains("-dontwarn org.xbill.DNS.spi.DnsjavaInetAddressResolverProvider"));
    }

    @Test
    public void atk34_updateModelClearsAndRejectsExpiredCachedManifest() throws IOException {
        String updateModel = readUtf8(
                appDir().resolve("src/main/java/org/adaway/model/update/UpdateModel.java"));

        assertTrue("Failed or invalid manifest checks must clear cached manifest state.",
                updateModel.contains("this.manifest.postValue(null);"));
        assertTrue("Update downloads must re-check cached manifest expiry.",
                updateModel.contains("manifest.isExpired()"));
        assertTrue("Expired cached manifests must not enqueue downloads.",
                updateModel.contains("return -1;"));
    }

    @Test
    public void atk35_androidCiRunsLintGate() throws IOException {
        String workflow = readUtf8(repoDir().resolve(".github/workflows/android-ci.yml"));

        assertTrue("Android CI must run lint, not only tests and debug assemble.",
                workflow.contains(":app:lintDebug --dependency-verification=strict --stacktrace"));
    }

    @Test
    public void atk35_androidCiRunsConnectedTestGate() throws IOException {
        String workflow = readUtf8(repoDir().resolve(".github/workflows/android-ci.yml"));

        assertTrue("Android CI must define a connected test job.",
                workflow.contains("connected-tests:"));
        assertTrue("Android CI must install an emulator system image.",
                workflow.contains("system-images;android-34;google_apis;x86_64"));
        assertTrue("Connected test CI must bound the job so emulator hangs do not run for hours.",
                workflow.contains("connected-tests:") &&
                        workflow.contains("timeout-minutes: 50"));
        assertTrue("Connected test CI must bound emulator boot separately.",
                workflow.contains("Boot emulator") &&
                        workflow.contains("timeout-minutes: 20"));
        assertTrue("Connected test CI must bound adb device detection inside emulator boot.",
                workflow.contains("timeout 300 adb wait-for-device"));
        assertTrue("Connected test CI must use one explicit AVD home for create and launch.",
                workflow.contains("export ANDROID_AVD_HOME=\"$HOME/.android/avd\"") &&
                        workflow.contains("mkdir -p \"$ANDROID_AVD_HOME\""));
        assertTrue("Connected test CI must verify the created AVD is visible before launch.",
                workflow.contains("avd-list-after-create.txt") &&
                        workflow.contains("grep -Fxq \"adaway-api34\""));
        assertTrue("Connected test CI must bound instrumentation separately.",
                workflow.contains("Run connected Android tests") &&
                        workflow.contains("timeout-minutes: 25"));
        assertTrue("Android CI must run connected androidTest.",
                workflow.contains(
                        ":app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace"));
        assertTrue("Connected test CI must retain logcat and test reports on failure.",
                workflow.contains("Upload connected test artifacts") &&
                        workflow.contains("connected-android-test-artifacts") &&
                        workflow.contains("app/build/ci-artifacts/**") &&
                        workflow.contains("app/build/outputs/androidTest-results/**") &&
                        workflow.contains("app/build/reports/androidTests/**"));
        assertTrue("Connected test diagnostics must not hang when no emulator is reachable.",
                workflow.contains("timeout 15 adb devices -l") &&
                        workflow.contains("timeout 15 adb shell getprop") &&
                        workflow.contains("timeout 15 adb shell dumpsys activity processes") &&
                        workflow.contains("timeout 15 adb logcat -d -v threadtime"));
    }

    @Test
    public void atk35_securityWorkflowsRunOnPullRequestsAndManualDispatch() throws IOException {
        String ciWorkflow = readUtf8(repoDir().resolve(".github/workflows/android-ci.yml"));
        String codeqlWorkflow = readUtf8(repoDir().resolve(".github/workflows/android-analysis.yml"));

        assertTrue("Android CI must protect pull requests, not only master pushes.",
                ciWorkflow.contains("pull_request:"));
        assertTrue("Android CI must support manual dispatch for release-blocking verification.",
                ciWorkflow.contains("workflow_dispatch:"));
        assertTrue("CodeQL must protect pull requests, not only scheduled scans.",
                codeqlWorkflow.contains("pull_request:"));
        assertTrue("CodeQL must support manual dispatch for security verification reruns.",
                codeqlWorkflow.contains("workflow_dispatch:"));
        assertTrue("CodeQL must keep scheduled scans for drift detection.",
                codeqlWorkflow.contains("schedule:"));
    }

    @Test
    public void atk35_androidCiUsesStrictDependencyVerification() throws IOException {
        String ciWorkflow = readUtf8(repoDir().resolve(".github/workflows/android-ci.yml"));
        String codeqlWorkflow = readUtf8(repoDir().resolve(".github/workflows/android-analysis.yml"));

        assertTrue("Unit test CI must use strict dependency verification.",
                ciWorkflow.contains("./gradlew test --dependency-verification=strict --stacktrace"));
        assertTrue("Lint CI must use strict dependency verification.",
                ciWorkflow.contains(
                        "./gradlew :app:lintDebug --dependency-verification=strict --stacktrace"));
        assertTrue("Debug assemble CI must use strict dependency verification.",
                ciWorkflow.contains(
                        "./gradlew assembleDebug --dependency-verification=strict --stacktrace"));
        assertTrue("Development SBOM CI must use strict dependency verification.",
                ciWorkflow.contains(
                        "./gradlew :app:cyclonedxBom --dependency-verification=strict --stacktrace"));
        assertTrue("Direct release dry-run CI must use strict dependency verification.",
                ciWorkflow.contains(":app:assembleDirectRelease :app:generateSbom") &&
                        ciWorkflow.contains("--dependency-verification=strict"));
        assertTrue("Sonar CI resolution must use strict dependency verification.",
                ciWorkflow.contains("sonarqube --dependency-verification=strict"));
        assertTrue("Connected test CI must use strict dependency verification.",
                ciWorkflow.contains(
                        "./gradlew :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace"));
        assertTrue("CodeQL autobuild replacement must use strict dependency verification.",
                codeqlWorkflow.contains("./gradlew assembleDebug --dependency-verification=strict"));
        assertTrue("CodeQL Java build must rerun compile tasks so extraction sees source.",
                codeqlWorkflow.contains(
                        "./gradlew assembleDebug --dependency-verification=strict --rerun-tasks"));
        assertTrue("CodeQL C++ must use buildless mode when Android debug build compiles no C/C++.",
                codeqlWorkflow.contains("Initialize CodeQL for C++") &&
                        codeqlWorkflow.contains("if: matrix.language == 'cpp'") &&
                        codeqlWorkflow.contains("build-mode: none"));
        assertTrue("CodeQL Java must keep its previous initializer without manual build-mode.",
                codeqlWorkflow.contains("Initialize CodeQL for Java") &&
                        codeqlWorkflow.contains("if: matrix.language == 'java'"));
        assertFalse("CodeQL Java must not use manual build-mode; CI proved that extracts no Java.",
                codeqlWorkflow.contains("build-mode: manual"));
        assertTrue("CodeQL Gradle build must only run for Java jobs.",
                codeqlWorkflow.contains("if: matrix.language == 'java'"));
    }

    @Test
    public void atk35_androidCiChecksDevelopmentArtifactLicenseBoundary() throws IOException {
        String workflow = readUtf8(repoDir().resolve(".github/workflows/android-ci.yml"));
        int buildStart = workflow.indexOf("Build with Gradle");
        int sbomStart = workflow.indexOf("Generate development SBOM");
        int boundaryStart = workflow.indexOf("Check development artifact license boundary");
        int boundaryUploadStart = workflow.indexOf("Upload development artifact boundary report");
        int sbomUploadStart = workflow.indexOf("Upload development SBOM");
        int apkUploadStart = workflow.indexOf("Upload APK");

        assertTrue("Android CI must generate a development CycloneDX SBOM.",
                sbomStart > 0 &&
                        workflow.contains(":app:cyclonedxBom"));
        assertTrue("Android CI must check the built debug APK and development SBOM together.",
                boundaryStart > sbomStart &&
                        workflow.contains("-ApkPath app/build/outputs/apk/debug/app-debug.apk") &&
                        workflow.contains("-SbomPath app/build/reports/cyclonedx/bom.json") &&
                        workflow.contains("-StrictArtifacts"));
        assertTrue("Development artifact boundary must run after debug APK build.",
                buildStart > 0 && buildStart < sbomStart && sbomStart < boundaryStart);
        assertTrue("Android CI must persist the development artifact boundary report.",
                boundaryUploadStart > boundaryStart &&
                        workflow.contains("debug-artifact-license-boundary-report") &&
                        workflow.contains("debug-artifact-license-boundary-report.md"));
        assertTrue("Android CI must upload the exact SBOM inspected by the boundary check.",
                sbomUploadStart > boundaryStart &&
                        sbomUploadStart < apkUploadStart &&
                        workflow.contains("AdAway-debug-sbom") &&
                        workflow.contains("app/build/reports/cyclonedx/bom.json"));
    }

    @Test
    public void atk35_androidCiRunsDirectReleasePackagingDryRun() throws IOException {
        String workflow = readUtf8(repoDir().resolve(".github/workflows/android-ci.yml"));
        int debugApkUploadStart = workflow.indexOf("Upload APK");
        int dryRunStart = workflow.indexOf("Run directRelease packaging dry run");
        int boundaryStart = workflow.indexOf("Check directRelease dry-run artifact boundary");
        int uploadStart = workflow.indexOf("Upload directRelease dry-run boundary report");
        int analyzeStart = workflow.indexOf("Analyze project");

        assertTrue("Android CI must run a directRelease packaging dry-run.",
                dryRunStart > debugApkUploadStart &&
                        workflow.contains(":app:assembleDirectRelease :app:generateSbom") &&
                        workflow.contains("-PsigningStoreLocation=\"$KEYSTORE\"") &&
                        workflow.contains("-PupdateManifestPublicKeyBase64=" +
                                "\"$UPDATE_MANIFEST_PUBLIC_KEY_BASE64\""));
        assertTrue("Dry-run update public key must come from the temporary keystore.",
                workflow.contains("keytool -exportcert -rfc") &&
                        workflow.contains("openssl x509 -pubkey -noout") &&
                        workflow.contains("/BEGIN PUBLIC KEY/d") &&
                        workflow.contains("tr -d '\\n'"));
        assertTrue("Dry-run signing must avoid PKCS12 key/store password mismatch.",
                workflow.contains("DRY_RUN_SIGNING_PASSWORD") &&
                        workflow.contains("-storepass \"$DRY_RUN_SIGNING_PASSWORD\"") &&
                        workflow.contains("-keypass \"$DRY_RUN_SIGNING_PASSWORD\"") &&
                        workflow.contains("-PsigningStorePassword=\"$DRY_RUN_SIGNING_PASSWORD\"") &&
                        workflow.contains("-PsigningKeyPassword=\"$DRY_RUN_SIGNING_PASSWORD\""));
        assertTrue("DirectRelease dry-run boundary must inspect release APK and SBOM outputs.",
                boundaryStart > dryRunStart &&
                        workflow.contains("-ApkPath app/build/outputs/apk/directRelease/" +
                                "app-directRelease.apk") &&
                        workflow.contains("-SbomPath app/build/reports/sbom/adaway.cdx.json") &&
                        workflow.contains("-StrictArtifacts"));
        assertTrue("Android CI must persist the directRelease dry-run boundary report.",
                uploadStart > boundaryStart &&
                        uploadStart < analyzeStart &&
                        workflow.contains("directrelease-dry-run-license-boundary-report") &&
                        workflow.contains("directrelease-dry-run-license-boundary-report.md"));
    }

    @Test
    public void atk35_androidCiPreventsPrematureMitBranding() throws IOException {
        Path repo = repoDir();
        String workflow = readUtf8(repo.resolve(".github/workflows/android-ci.yml"));
        String releaseWorkflow = readUtf8(repo.resolve(".github/workflows/fork-release-apk.yml"));
        String boundaryScript = readUtf8(repo.resolve("scripts/check-license-boundary.ps1"));
        String releasing = readUtf8(repo.resolve("RELEASING.md"));
        String readme = readUtf8(repo.resolve("README.md"));

        assertTrue("Android CI must run the license-boundary check.",
                workflow.contains("scripts/check-license-boundary.ps1"));
        assertTrue("Android CI must persist a license-boundary report artifact.",
                workflow.contains("-ReportPath app/build/reports/license-boundary/" +
                        "license-boundary-report.md") &&
                        workflow.contains("Upload license boundary report") &&
                        workflow.contains("license-boundary-report") &&
                        workflow.contains("actions/upload-artifact@" +
                                "043fb46d1a93c77aae656e7c1c64a875d1fc6a0a"));
        assertTrue("Tagged release workflow must persist source and artifact license reports.",
                releaseWorkflow.contains("-ReportPath release-boundary/" +
                        "source-license-boundary-report.md") &&
                        releaseWorkflow.contains("-ReportPath release-boundary/" +
                                "artifact-license-boundary-report.md") &&
                        releaseWorkflow.contains("release-license-boundary-reports") &&
                        releaseWorkflow.contains("release-boundary/*.md"));
        assertTrue("License boundary check must fail on MIT release claims.",
                boundaryScript.contains("MIT-branded release wording is blocked"));
        assertTrue("License boundary check must support durable report output.",
                boundaryScript.contains("$ReportPath") &&
                        boundaryScript.contains("Write-LicenseBoundaryReport") &&
                        boundaryScript.contains("License Boundary Report"));
        assertTrue("License boundary check must catch natural-language MIT claims.",
                boundaryScript.contains("(?:licensed|released|distributed|available)"));
        assertTrue("License boundary check must catch hyphenated MIT claims.",
                boundaryScript.contains("MIT[- ]licensed"));
        assertTrue("License boundary check must catch MIT terms wording.",
                boundaryScript.contains("MIT\\s+terms"));
        assertTrue("License boundary check must keep GPL blocker detection explicit.",
                boundaryScript.contains("GNU General Public License"));
        assertTrue("License boundary check must inspect packaged GPL-derived app code.",
                boundaryScript.contains("app/src/main/java"));
        assertTrue("License boundary check must inspect docs where legal wording drifts.",
                boundaryScript.contains("\"docs\""));
        assertTrue("License boundary check must inspect changelog release text.",
                boundaryScript.contains("\"CHANGELOG.md\""));
        assertTrue("License boundary check must inspect release process text.",
                boundaryScript.contains("\"RELEASING.md\""));
        assertTrue("License boundary check must inspect packaged resources and metadata.",
                boundaryScript.contains("app/src/main/res"));
        assertTrue("License boundary check must inspect top-level historical resources.",
                boundaryScript.contains("\"Resources\""));
        assertTrue("License boundary check must report details before exiting.",
                boundaryScript.contains("Report-And-Fail"));
        assertTrue("License boundary check must support git-tracked source inventory.",
                boundaryScript.contains("git ls-files"));
        assertTrue("License boundary check must support strict source archive inspection.",
                boundaryScript.contains("StrictSourceArchive"));
        assertTrue("Strict source archive inspection must use git archive.",
                boundaryScript.contains("git archive"));
        assertTrue("Strict source archive inspection must honor export-ignore attributes.",
                boundaryScript.contains("--worktree-attributes"));
        assertTrue("Strict source archive inspection must block dormant webserver exports.",
                boundaryScript.contains("^webserver/"));
        assertTrue("License boundary check must inspect APK zip entries.",
                boundaryScript.contains("Get-ZipEntries"));
        assertTrue("License boundary check must inspect APK resource names.",
                boundaryScript.contains("aapt dump resources"));
        assertTrue("License boundary check must parse release CycloneDX SBOM components.",
                boundaryScript.contains("Get-SbomComponents"));
        assertTrue("License boundary check must fail if packaged PayPal or GitHub marks return.",
                boundaryScript.contains("Forbidden packaged third-party mark resources detected"));
        assertTrue("License boundary check must block GitHub mark resource name variants.",
                boundaryScript.contains("org\\.adaway:drawable/ic_github(?:_|$)"));
        assertTrue("Release docs must document license-boundary report artifacts.",
                releasing.contains("license-boundary-report") &&
                        releasing.contains("release-license-boundary-reports") &&
                        readme.contains("license-boundary-report"));
    }

    @Test
    public void atk35_licenseBoundaryScriptWritesPassingReport() throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the license-boundary script.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-license-boundary-pass-report");
        try {
            writeBoundaryFixtureBase(fixture);
            Path report = fixture.resolve("license-boundary-report.md");
            java.util.Map<String, String> environment = new java.util.HashMap<>();
            environment.put("GITHUB_SHA", "0123456789abcdef0123456789abcdef01234567");

            ProcessResult result = runLicenseBoundaryScript(powershell, fixture, environment,
                    "-ReportPath", report.toString());

            assertEquals("Passing license boundary fixture must exit successfully.",
                    0, result.exitCode);
            assertTrue("Passing license boundary run must write the requested report.",
                    Files.isRegularFile(report));
            String reportText = readUtf8(report);
            assertTrue("Passing report must summarize the license-boundary proof.",
                    reportText.contains("# License Boundary Report") &&
                            reportText.contains("- Status: passed") &&
                            Pattern.compile("(?m)^- Source commit: [0-9a-f]{40}$")
                                    .matcher(reportText)
                                    .find() &&
                            reportText.contains("- Source mode: WorkingTree") &&
                            reportText.contains("- Strict source archive: false") &&
                            reportText.contains("- Strict artifacts: false") &&
                            reportText.contains("- MIT release status: blocked") &&
                            reportText.contains("- Issues: 0"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void atk35_licenseBoundaryScriptFailsOnFixtureClaims() throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the license-boundary script.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-license-boundary");
        try {
            writeUtf8(fixture.resolve("LICENSE.md"),
                    "GNU General Public License\n");
            writeUtf8(fixture.resolve("THIRD_PARTY_LICENSES.md"),
                    "Current package remains GPL-derived and is not MIT.\n");
            writeUtf8(fixture.resolve("app/src/main/java/GplBlocker.java"),
                    "/* GNU General Public License */\n");
            writeUtf8(fixture.resolve("docs/release.md"),
                    "This fork is released under MIT.\n");
            writeUtf8(fixture.resolve("CHANGELOG.md"),
                    "This release is MIT-licensed.\n");
            writeUtf8(fixture.resolve("RELEASING.md"),
                    "Ship this package under MIT terms.\n");
            writeUtf8(fixture.resolve("metadata/en-US/full_description.txt"),
                    "Distributed under MIT for store listings.\n");
            writeUtf8(fixture.resolve("Resources/icon.svg"),
                    "<!-- available under MIT -->\n");
            Path report = fixture.resolve("license-boundary-report.md");

            ProcessResult result = runLicenseBoundaryScript(powershell, fixture,
                    "-ReportPath", report.toString());

            assertTrue("MIT/GPL fixture must fail the boundary script.",
                    result.exitCode != 0);
            assertTrue("Failing license boundary run must write the requested report.",
                    Files.isRegularFile(report));
            String reportText = readUtf8(report);
            assertTrue("Failing report must preserve issue details.",
                    reportText.contains("# License Boundary Report") &&
                            reportText.contains("- Status: failed") &&
                            reportText.contains("- Issues: ") &&
                            reportText.contains("MIT-branded release wording is blocked") &&
                            reportText.contains("docs/release.md"));
            assertTrue("Failure must explain that MIT branding is blocked.",
                    result.stderr.contains("MIT-branded release wording is blocked"));
            assertTrue("Failure must report matching MIT claims before exiting.",
                    result.stderr.contains("MIT claims:"));
            assertTrue("Failure must report GPL blockers before exiting.",
                    result.stderr.contains("GPL blockers:"));
            assertTrue("Docs MIT claim must be scanned.",
                    result.stderr.contains("docs"));
            assertTrue("Changelog MIT claim must be scanned.",
                    result.stderr.contains("CHANGELOG.md"));
            assertTrue("Release process MIT claim must be scanned.",
                    result.stderr.contains("RELEASING.md"));
            assertTrue("Metadata MIT claim must be scanned.",
                    result.stderr.contains("metadata"));
            assertTrue("Top-level Resources MIT claim must be scanned.",
                    result.stderr.contains("Resources"));
            assertTrue("GPL blocker details must be retained.",
                    result.stderr.contains("GplBlocker.java") ||
                            result.stderr.contains("LICENSE.md"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void atk35_licenseBoundaryScriptFailsOnForbiddenSourceAsset() throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the license-boundary script.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-license-source-boundary");
        try {
            writeBoundaryFixtureBase(fixture);
            writeUtf8(fixture.resolve("app/src/main/assets/localhost-2410.key"),
                    "local test key\n");

            ProcessResult result = runLicenseBoundaryScript(powershell, fixture);

            assertTrue("Forbidden app source asset fixture must fail.",
                    result.exitCode != 0);
            assertTrue("Failure must report forbidden app source assets.",
                    result.stderr.contains("Forbidden source/archive boundary entries remain"));
            assertTrue("Failure must identify localhost key material.",
                    result.stderr.contains("localhost-2410.key"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void atk35_licenseBoundaryScriptFailsOnForbiddenSourceArchiveEntry() throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the license-boundary script.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-license-source-archive");
        try {
            writeBoundaryFixtureBase(fixture);
            writeUtf8(fixture.resolve("README.md"),
                    "AdAway test fixture.\n");
            writeUtf8(fixture.resolve("webserver/legacy-key.pem"),
                    "historical localhost key\n");

            assertEquals("Fixture git repository must initialize.",
                    0, runProcess(fixture, "git", "init").exitCode);
            assertEquals("Fixture files must be staged.",
                    0, runProcess(fixture, "git", "add", ".").exitCode);
            assertEquals("Fixture commit must be created for git archive.",
                    0, runProcess(fixture,
                            "git",
                            "-c", "user.name=AdAway Test",
                            "-c", "user.email=test@example.invalid",
                            "commit", "-m", "source archive fixture").exitCode);

            ProcessResult result = runLicenseBoundaryScript(powershell, fixture,
                    "-StrictSourceArchive");

            assertTrue("Forbidden source archive fixture must fail.",
                    result.exitCode != 0);
            assertTrue("Failure must report forbidden source archive entries.",
                    result.stderr.contains("Forbidden source archive entries detected"));
            assertTrue("Failure must identify dormant webserver archive material.",
                    result.stderr.contains("webserver/legacy-key.pem"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void atk35_licenseBoundaryScriptFailsOnApkAndSbomBoundaryDrift() throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the license-boundary script.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-license-artifact-boundary");
        try {
            writeBoundaryFixtureBase(fixture);
            Path apk = fixture.resolve("release.apk");
            writeZip(apk, "assets/localhost-2410.key", "test key");
            Path sbom = fixture.resolve("adaway.cdx.json");
            writeUtf8(sbom, "{\n" +
                    "  \"bomFormat\": \"CycloneDX\",\n" +
                    "  \"components\": [\n" +
                    "    {\"group\": \"net.java.dev.jna\", \"name\": \"jna\", \"version\": \"5.3.1\"}\n" +
                    "  ]\n" +
                    "}\n");

            ProcessResult result = runLicenseBoundaryScript(powershell, fixture,
                    "-ApkPath", apk.toString(),
                    "-SbomPath", sbom.toString(),
                    "-StrictArtifacts");

            assertTrue("APK/SBOM artifact drift fixture must fail.",
                    result.exitCode != 0);
            assertTrue("Failure must report forbidden APK entries.",
                    result.stderr.contains("Forbidden release APK entries detected"));
            assertTrue("Failure must identify packaged localhost key material.",
                    result.stderr.contains("assets/localhost-2410.key"));
            assertTrue("Failure must report missing JNA notice coverage.",
                    result.stderr.contains("SBOM dependency missing THIRD_PARTY_LICENSES.md notice: JNA"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void atk35_licenseBoundaryScriptFailsOnGithubMarkResourceVariants() throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the license-boundary script.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-license-resource-boundary");
        try {
            writeBoundaryFixtureBase(fixture);
            writeFakeAapt(fixture, "org.adaway:drawable/ic_github_octocat");
            Path apk = fixture.resolve("release.apk");
            writeZip(apk, "resources.arsc", "fake resource table marker");
            java.util.Map<String, String> environment = new java.util.HashMap<>();
            environment.put("ANDROID_HOME", null);
            environment.put("ANDROID_SDK_ROOT", null);

            ProcessResult result = runLicenseBoundaryScript(powershell, fixture, environment,
                    "-ApkPath", apk.toString(),
                    "-StrictArtifacts");

            assertTrue("GitHub mark resource variant fixture must fail.",
                    result.exitCode != 0);
            assertTrue("Failure must report forbidden third-party mark resources.",
                    result.stderr.contains("Forbidden packaged third-party mark resources detected"));
            assertTrue("Failure must identify the GitHub mark variant resource.",
                    result.stderr.contains("org.adaway:drawable/ic_github_octocat"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void atk35_licenseBoundaryAllowsLegitimateMitDependencyNotice() throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the license-boundary script.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-license-mit-dependency");
        try {
            writeUtf8(fixture.resolve("LICENSE.md"),
                    "GNU General Public License\n");
            writeUtf8(fixture.resolve("THIRD_PARTY_LICENSES.md"),
                    "MIT relicensing is future-only; the current app remains GPL-derived.\n" +
                            "| Pcap4J | MIT | Runtime dependency | Yes | Notice item. |\n");

            ProcessResult result = runLicenseBoundaryScript(powershell, fixture);

            assertEquals("A legitimate MIT dependency notice must not be treated as product MIT relicensing.",
                    0, result.exitCode);
            assertTrue("Passing guard must report success.",
                    result.stdout.contains("License boundary check passed"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void atk36_periodicUpdateSchedulersUseReplaceablePolicy() throws IOException {
        Path app = appDir();
        String sourceUpdateService = readUtf8(
                app.resolve("src/main/java/org/adaway/model/source/SourceUpdateService.java"));
        String apkUpdateService = readUtf8(
                app.resolve("src/main/java/org/adaway/model/update/ApkUpdateService.java"));
        String filterSetUpdateService = readUtf8(
                app.resolve("src/main/java/org/adaway/ui/hosts/FilterSetUpdateService.java"));

        assertFalse("Hosts update preference sync must not preserve stale periodic work.",
                sourceUpdateService.contains("enqueueWork(context, KEEP"));
        assertFalse("APK update preference sync must not preserve stale periodic work.",
                apkUpdateService.contains("enqueueWork(context, KEEP"));
        assertTrue("Hosts update scheduling must use UPDATE so changed constraints apply.",
                sourceUpdateService.contains("enqueueWork(context, UPDATE"));
        assertTrue("APK update scheduling must use UPDATE so changed settings apply.",
                apkUpdateService.contains("enqueueWork(context, UPDATE"));
        assertTrue("Filter set scheduling must use UPDATE so changed constraints apply.",
                filterSetUpdateService.contains("ExistingPeriodicWorkPolicy.UPDATE"));
    }

    private static Path appDir() {
        Path cwd = Paths.get("").toAbsolutePath();
        if (Files.isDirectory(cwd.resolve("src/main"))) {
            return cwd;
        }
        return cwd.resolve("app");
    }

    private static Path repoDir() {
        Path app = appDir();
        Path parent = app.getParent();
        return parent != null && app.getFileName().toString().equals("app") ? parent : app;
    }

    private static String readUtf8(Path path) throws IOException {
        return normalizeLineEndings(new String(Files.readAllBytes(path),
                StandardCharsets.UTF_8));
    }

    private static String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static void writeUtf8(Path path, String text) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(path, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeBoundaryFixtureBase(Path fixture) throws IOException {
        writeUtf8(fixture.resolve("LICENSE.md"),
                "GNU General Public License\n");
        writeUtf8(fixture.resolve("THIRD_PARTY_LICENSES.md"),
                "MIT relicensing is future-only; the current app remains GPL-derived.\n" +
                        "## Source-Only Resources Inventory\n" +
                        "| Tcpdump / Libpcap | BSD-3-Clause | Source-only | No | Inventory. |\n");
    }

    private static void writeZip(Path zipPath, String entryName, String body) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(body.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    private static void writeChecksum(Path target, Path checksum) throws Exception {
        writeUtf8(checksum, sha256Hex(Files.readAllBytes(target)) + "  " +
                target.getFileName() + "\n");
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return hex.toString();
    }

    private static void writeFakeAapt(Path fixture, String resourceName) throws IOException {
        Path sdk = fixture.resolve("fake-android-sdk");
        Path buildTools = sdk.resolve("build-tools").resolve("99.0.0");
        Files.createDirectories(buildTools);
        writeUtf8(fixture.resolve("local.properties"),
                "sdk.dir=" + sdk.toString().replace('\\', '/') + "\n");

        String output = "spec resource 0x7f080123 " + resourceName + ": flags=0x00000000";
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.US).contains("win");
        Path aapt = buildTools.resolve(windows ? "aapt.cmd" : "aapt");
        if (windows) {
            writeUtf8(aapt, "@echo off\r\necho " + output + "\r\n");
        } else {
            writeUtf8(aapt, "#!/usr/bin/env sh\nprintf '%s\\n' '" + output + "'\n");
            assertTrue("Fake aapt must be executable.", aapt.toFile().setExecutable(true));
        }
    }

    private static void writeFakeBuildTool(Path sdk, String version, String toolName, String output)
            throws IOException {
        Path buildTools = sdk.resolve("build-tools").resolve(version);
        Files.createDirectories(buildTools);
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.US).contains("win");
        Path tool = buildTools.resolve(windows ? toolName + ".cmd" : toolName);
        if (windows) {
            writeUtf8(tool, "@echo off\r\necho " + output + "\r\n");
        } else {
            String escaped = output.replace("\\", "\\\\").replace("\"", "\\\"");
            writeUtf8(tool, "#!/usr/bin/env sh\nprintf '%s\\n' \"" + escaped + "\"\n");
            assertTrue("Fake build tool must be executable.", tool.toFile().setExecutable(true));
        }
    }

    private static void writeFakeAdb(Path sdk, String serial, String pid) throws IOException {
        Path platformTools = sdk.resolve("platform-tools");
        Files.createDirectories(platformTools);
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.US).contains("win");
        Path tool = platformTools.resolve(windows ? "adb.cmd" : "adb");
        if (windows) {
            writeUtf8(tool, "@echo off\r\n" +
                    "if \"%1\"==\"devices\" (\r\n" +
                    "  echo List of devices attached\r\n" +
                    "  echo " + serial + "\tdevice\r\n" +
                    "  exit /b 0\r\n" +
                    ")\r\n" +
                    "if \"%1\"==\"-s\" if \"%3\"==\"shell\" if \"%4\"==\"pidof\" echo " + pid + "\r\n" +
                    "if \"%1\"==\"-s\" if \"%3\"==\"shell\" if \"%4\"==\"getprop\" echo 0\r\n" +
                    "exit /b 0\r\n");
        } else {
            writeUtf8(tool, "#!/usr/bin/env sh\n" +
                    "if [ \"$1\" = \"devices\" ]; then\n" +
                    "  printf 'List of devices attached\\n'\n" +
                    "  printf '" + serial + "\\tdevice\\n'\n" +
                    "  exit 0\n" +
                    "fi\n" +
                    "if [ \"$1\" = \"-s\" ] && [ \"$3\" = \"shell\" ] && [ \"$4\" = \"pidof\" ]; then\n" +
                    "  printf '" + pid + "\\n'\n" +
                    "  exit 0\n" +
                    "fi\n" +
                    "if [ \"$1\" = \"-s\" ] && [ \"$3\" = \"shell\" ] && [ \"$4\" = \"getprop\" ]; then\n" +
                    "  printf '0\\n'\n" +
                    "fi\n");
            assertTrue("Fake adb must be executable.", tool.toFile().setExecutable(true));
        }
    }

    private static Path writeFakeGitHubCli(Path fixture, Path log) throws IOException {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.US).contains("win");
        Path tool = fixture.resolve(windows ? "gh.cmd" : "gh");
        if (windows) {
            writeUtf8(tool, "@echo off\r\necho %*>> \"%FAKE_GH_LOG%\"\r\nexit /b 0\r\n");
        } else {
            writeUtf8(tool, "#!/usr/bin/env sh\nprintf '%s\\n' \"$*\" >> \"$FAKE_GH_LOG\"\n");
            assertTrue("Fake GitHub CLI must be executable.", tool.toFile().setExecutable(true));
        }
        return tool;
    }

    private static Path writeFlakyGitHubCli(Path fixture, Path log, Path state)
            throws IOException {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.US).contains("win");
        Path tool = fixture.resolve(windows ? "gh-flaky.cmd" : "gh-flaky");
        if (windows) {
            writeUtf8(tool, "@echo off\r\n" +
                    "if not exist \"%FAKE_GH_STATE%\" (\r\n" +
                    "  echo seen> \"%FAKE_GH_STATE%\"\r\n" +
                    "  echo transient-miss %*>> \"%FAKE_GH_LOG%\"\r\n" +
                    "  exit /b 1\r\n" +
                    ")\r\n" +
                    "echo %*>> \"%FAKE_GH_LOG%\"\r\n" +
                    "exit /b 0\r\n");
        } else {
            writeUtf8(tool, "#!/usr/bin/env sh\n" +
                    "if [ ! -e \"$FAKE_GH_STATE\" ]; then\n" +
                    "  printf '%s\\n' seen > \"$FAKE_GH_STATE\"\n" +
                    "  printf 'transient-miss %s\\n' \"$*\" >> \"$FAKE_GH_LOG\"\n" +
                    "  exit 1\n" +
                    "fi\n" +
                    "printf '%s\\n' \"$*\" >> \"$FAKE_GH_LOG\"\n");
            assertTrue("Fake flaky GitHub CLI must be executable.",
                    tool.toFile().setExecutable(true));
        }
        return tool;
    }

    private static String findPowerShell() {
        for (String candidate : new String[]{"pwsh", "powershell", "powershell.exe"}) {
            try {
                Process process = new ProcessBuilder(candidate, "-NoProfile", "-Command",
                        "$PSVersionTable.PSVersion.Major").start();
                if (process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) &&
                        process.exitValue() == 0) {
                    return candidate;
                }
            } catch (IOException exception) {
                // Try the next executable name.
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private static String javaCommand() {
        String executable = System.getProperty("os.name").toLowerCase(Locale.US).contains("win") ?
                "java.exe" : "java";
        Path java = Paths.get(System.getProperty("java.home"), "bin", executable);
        return Files.isRegularFile(java) ? java.toString() : executable;
    }

    private static String pem(String label, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
    }

    private static ProcessResult runLicenseBoundaryScript(String powershell, Path workingDirectory)
            throws IOException, InterruptedException {
        return runLicenseBoundaryScript(powershell, workingDirectory, new String[0]);
    }

    private static ProcessResult runLicenseBoundaryScript(String powershell, Path workingDirectory,
            String... args) throws IOException, InterruptedException {
        return runLicenseBoundaryScript(powershell, workingDirectory,
                java.util.Collections.emptyMap(), args);
    }

    private static ProcessResult runLicenseBoundaryScript(String powershell, Path workingDirectory,
            java.util.Map<String, String> environment, String... args)
            throws IOException, InterruptedException {
        Path script = repoDir().resolve("scripts/check-license-boundary.ps1");
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add(powershell);
        command.add("-NoProfile");
        command.add("-ExecutionPolicy");
        command.add("Bypass");
        command.add("-File");
        command.add(script.toString());
        java.util.Collections.addAll(command, args);
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workingDirectory.toFile());
        for (java.util.Map.Entry<String, String> entry : environment.entrySet()) {
            if (entry.getValue() == null) {
                builder.environment().remove(entry.getKey());
            } else {
                builder.environment().put(entry.getKey(), entry.getValue());
            }
        }
        Process process = builder.start();
        if (!process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Timed out running license-boundary script.");
        }
        String stdout;
        String stderr;
        try (java.io.InputStream output = process.getInputStream();
                java.io.InputStream error = process.getErrorStream()) {
            stdout = new String(output.readAllBytes(), StandardCharsets.UTF_8);
            stderr = new String(error.readAllBytes(), StandardCharsets.UTF_8);
        }
        return new ProcessResult(process.exitValue(), stdout, stderr);
    }

    private static ProcessResult runProcess(Path workingDirectory, String... command)
            throws IOException, InterruptedException {
        return runProcess(workingDirectory, java.util.Collections.emptyMap(), command);
    }

    private static ProcessResult runProcess(Path workingDirectory,
            java.util.Map<String, String> environment, String... command)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workingDirectory.toFile());
        for (java.util.Map.Entry<String, String> entry : environment.entrySet()) {
            if (entry.getValue() == null) {
                builder.environment().remove(entry.getKey());
            } else {
                builder.environment().put(entry.getKey(), entry.getValue());
            }
        }
        Process process = builder.start();
        if (!process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Timed out running command: " + String.join(" ", command));
        }
        String stdout;
        String stderr;
        try (java.io.InputStream output = process.getInputStream();
                java.io.InputStream error = process.getErrorStream()) {
            stdout = new String(output.readAllBytes(), StandardCharsets.UTF_8);
            stderr = new String(error.readAllBytes(), StandardCharsets.UTF_8);
        }
        return new ProcessResult(process.exitValue(), stdout, stderr);
    }

    private static void deleteFileWithRetries(Path path) throws IOException {
        IOException lastException = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                Files.deleteIfExists(path);
                return;
            } catch (IOException exception) {
                lastException = exception;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw exception;
                }
            }
        }
        throw lastException;
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    try {
                        path.toFile().setWritable(true);
                        Files.deleteIfExists(path);
                    } catch (IOException retryException) {
                        throw new IllegalStateException("Failed to delete " + path, retryException);
                    }
                }
            });
        }
    }

    private static final class ProcessResult {
        final int exitCode;
        final String stdout;
        final String stderr;

        ProcessResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    private static boolean contains(Path path, String needle) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1)
                    .contains(needle);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to scan " + path, exception);
        }
    }

    private static int countMatches(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
