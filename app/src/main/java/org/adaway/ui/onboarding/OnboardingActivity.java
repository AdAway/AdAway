package org.adaway.ui.onboarding;

import static android.app.Activity.RESULT_OK;
import static java.lang.Boolean.TRUE;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.topjohnwu.superuser.Shell;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.databinding.ActivityOnboardingBinding;
import org.adaway.helper.PreferenceHelper;
import org.adaway.helper.ThemeHelper;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.error.HostErrorException;
import org.adaway.ui.home.HomeActivity;
import org.adaway.util.log.SentryLog;

/**
 * Single-screen onboarding: choose VPN or Root protection method.
 *
 * Replaces the 3-screen WelcomeActivity wizard with a single card-based chooser.
 * Auto-selects VPN when device does not have root.
 *
 * The launcher intent is set to this Activity in AndroidManifest.xml. HomeActivity
 * checks if the method is UNDEFINED and redirects here on its own if somehow reached
 * before onboarding completes.
 */
public class OnboardingActivity extends AppCompatActivity {
    public static final String EXTRA_SKIP_AUTO_DETECT =
            "org.adaway.ui.onboarding.SKIP_AUTO_DETECT";
    private static final String STATE_SELECTED_METHOD = "selected_method";
    private static final String STATE_FINISH_AFTER_VPN = "finish_after_vpn_authorization";

    private ActivityOnboardingBinding binding;
    private ActivityResultLauncher<Intent> prepareVpnLauncher;

    // Track which method the user has chosen
    private AdBlockMethod selectedMethod = AdBlockMethod.UNDEFINED;
    private boolean finishAfterVpnAuthorization;

    // Card color resources
    private int cardNormalColor;
    private int cardSelectedColor;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(R.style.Theme_AdAway_NoActionBar_Content);
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);

        this.binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(this.binding.getRoot());

        this.cardNormalColor = getResources().getColor(R.color.cardBackground, null);
        this.cardSelectedColor = getResources().getColor(R.color.cardEnabledBackground, null);
        if (savedInstanceState != null) {
            this.selectedMethod = AdBlockMethod.fromCode(
                    savedInstanceState.getInt(STATE_SELECTED_METHOD, AdBlockMethod.UNDEFINED.toCode()));
            this.finishAfterVpnAuthorization = savedInstanceState.getBoolean(STATE_FINISH_AFTER_VPN);
        }

        this.prepareVpnLauncher = registerForActivityResult(
                new StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        onVpnSelected();
                    } else {
                        onVpnDenied();
                    }
                });

        this.binding.onboardingVpnCard.setOnClickListener(v -> trySelectVpn());
        this.binding.onboardingRootCard.setOnClickListener(v -> trySelectRoot());
        this.binding.onboardingStartButton.setOnClickListener(v -> startProtecting());
        updateMethodCards();

        // Auto-detect: if root is not available, pre-select VPN
        if (savedInstanceState == null
                && !getIntent().getBooleanExtra(EXTRA_SKIP_AUTO_DETECT, false)) {
            autoDetectAndPreselectMethod();
        }
    }

    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        outState.putInt(STATE_SELECTED_METHOD, this.selectedMethod.toCode());
        outState.putBoolean(STATE_FINISH_AFTER_VPN, this.finishAfterVpnAuthorization);
        super.onSaveInstanceState(outState);
    }

    // -----------------------------------------------------------------------------------------
    // Method selection
    // -----------------------------------------------------------------------------------------

    private void autoDetectAndPreselectMethod() {
        // Check root availability without blocking the main thread
        new Thread(() -> {
            boolean hasRoot = TRUE.equals(Shell.isAppGrantedRoot());
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (!hasRoot && this.selectedMethod == AdBlockMethod.UNDEFINED) {
                    // No root — pre-select VPN
                    preselectVpn();
                }
                // If root is available, let the user choose explicitly
            });
        }).start();
    }

    private void trySelectVpn() {
        this.finishAfterVpnAuthorization = false;
        Intent prepareIntent = VpnService.prepare(this);
        if (prepareIntent == null) {
            // VPN already authorized
            onVpnSelected();
        } else {
            this.prepareVpnLauncher.launch(prepareIntent);
        }
    }

    private void preselectVpn() {
        SentryLog.recordBreadcrumb("Onboarding: VPN preselected");
        this.selectedMethod = AdBlockMethod.VPN;
        this.binding.onboardingVpnCard.setCardBackgroundColor(this.cardSelectedColor);
        this.binding.onboardingRootCard.setCardBackgroundColor(this.cardNormalColor);
        this.binding.onboardingStartButton.setEnabled(true);
        updateMethodCards();
    }

    private void trySelectRoot() {
        new Thread(() -> {
            Shell.getShell();
            boolean hasRoot = TRUE.equals(Shell.isAppGrantedRoot());
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (hasRoot) {
                    onRootSelected();
                } else {
                    onRootDenied();
                }
            });
        }).start();
    }

    private void onVpnSelected() {
        SentryLog.recordBreadcrumb("Onboarding: VPN selected");
        this.selectedMethod = AdBlockMethod.VPN;
        this.binding.onboardingVpnCard.setCardBackgroundColor(this.cardSelectedColor);
        this.binding.onboardingRootCard.setCardBackgroundColor(this.cardNormalColor);
        this.binding.onboardingStartButton.setEnabled(true);
        updateMethodCards();
        if (this.finishAfterVpnAuthorization) {
            this.finishAfterVpnAuthorization = false;
            finishOnboarding(AdBlockMethod.VPN);
        }
    }

    private void onVpnDenied() {
        // VPN permission denied — check if always-on is blocking
        this.finishAfterVpnAuthorization = false;
        this.selectedMethod = AdBlockMethod.UNDEFINED;
        this.binding.onboardingVpnCard.setCardBackgroundColor(this.cardNormalColor);
        this.binding.onboardingStartButton.setEnabled(false);
        updateMethodCards();
        checkAlwaysOnVpn();
    }

    private void onRootSelected() {
        SentryLog.recordBreadcrumb("Onboarding: Root selected");
        this.selectedMethod = AdBlockMethod.ROOT;
        this.binding.onboardingRootCard.setCardBackgroundColor(this.cardSelectedColor);
        this.binding.onboardingVpnCard.setCardBackgroundColor(this.cardNormalColor);
        this.binding.onboardingStartButton.setEnabled(true);
        updateMethodCards();
    }

    private void onRootDenied() {
        this.selectedMethod = AdBlockMethod.UNDEFINED;
        this.binding.onboardingRootCard.setCardBackgroundColor(this.cardNormalColor);
        this.binding.onboardingStartButton.setEnabled(false);
        updateMethodCards();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.welcome_root_missing_title)
                .setMessage(R.string.welcome_root_missile_description)
                .setPositiveButton(R.string.button_close, null)
                .show();
    }

    private void checkAlwaysOnVpn() {
        int message = R.string.welcome_vpn_alwayson_description;
        try {
            String alwaysOn = Settings.Secure.getString(
                    getContentResolver(), "always_on_vpn_app");
            if (alwaysOn == null) return;
        } catch (SecurityException e) {
            message = R.string.welcome_vpn_alwayson_blocked_description;
        }
        final int finalMessage = message;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.welcome_vpn_alwayson_title)
                .setMessage(finalMessage)
                .setNegativeButton(R.string.button_close, null)
                .setPositiveButton(R.string.welcome_vpn_alwayson_settings_action,
                        (dialog, which) -> startActivity(
                                new Intent(Settings.ACTION_VPN_SETTINGS)))
                .show();
    }

    private void updateMethodCards() {
        boolean vpnSelected = this.selectedMethod == AdBlockMethod.VPN;
        boolean rootSelected = this.selectedMethod == AdBlockMethod.ROOT;

        this.binding.onboardingStartButton.setEnabled(vpnSelected || rootSelected);
        this.binding.onboardingVpnCard.setCardBackgroundColor(
                vpnSelected ? this.cardSelectedColor : this.cardNormalColor);
        this.binding.onboardingRootCard.setCardBackgroundColor(
                rootSelected ? this.cardSelectedColor : this.cardNormalColor);

        this.binding.onboardingVpnCard.setChecked(vpnSelected);
        this.binding.onboardingRootCard.setChecked(rootSelected);
        this.binding.onboardingVpnCard.setContentDescription(getString(
                R.string.onboarding_method_accessibility,
                getString(R.string.onboarding_vpn_title),
                getString(R.string.onboarding_vpn_desc),
                getString(vpnSelected
                        ? R.string.onboarding_method_selected
                        : R.string.onboarding_method_not_selected)));
        this.binding.onboardingRootCard.setContentDescription(getString(
                R.string.onboarding_method_accessibility,
                getString(R.string.onboarding_root_title),
                getString(R.string.onboarding_root_desc),
                getString(rootSelected
                        ? R.string.onboarding_method_selected
                        : R.string.onboarding_method_not_selected)));
    }

    // -----------------------------------------------------------------------------------------
    // Finish onboarding
    // -----------------------------------------------------------------------------------------

    private void startProtecting() {
        if (this.selectedMethod == AdBlockMethod.UNDEFINED) return;
        if (this.selectedMethod == AdBlockMethod.VPN) {
            startVpnProtection();
            return;
        }
        finishOnboarding(this.selectedMethod);
    }

    private void startVpnProtection() {
        Intent prepareIntent = VpnService.prepare(this);
        if (prepareIntent == null) {
            finishOnboarding(AdBlockMethod.VPN);
        } else {
            this.finishAfterVpnAuthorization = true;
            this.prepareVpnLauncher.launch(prepareIntent);
        }
    }

    private void finishOnboarding(AdBlockMethod method) {
        PreferenceHelper.setAbBlockMethod(this, method);
        if (method == AdBlockMethod.VPN && !applyVpnProtection()) {
            return;
        }
        Intent intent = new Intent(this, HomeActivity.class);
        intent.putExtra(HomeActivity.EXTRA_ONBOARDING_COMPLETE, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private boolean applyVpnProtection() {
        AdBlockModel adBlockModel =
                ((AdAwayApplication) getApplication()).getAdBlockModel();
        try {
            adBlockModel.apply();
            return true;
        } catch (HostErrorException exception) {
            SentryLog.recordBreadcrumb("Onboarding: VPN start failed");
            new MaterialAlertDialogBuilder(this)
                    .setTitle(exception.getError().getMessageKey())
                    .setMessage(exception.getError().getDetailsKey())
                    .setPositiveButton(R.string.button_close, null)
                    .show();
            return false;
        }
    }
}
