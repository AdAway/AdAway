package org.adaway.ui.welcome;

import static android.app.Activity.RESULT_OK;
import static android.provider.Settings.ACTION_VPN_SETTINGS;
import static org.adaway.model.adblocking.AdBlockMethod.ROOT;
import static org.adaway.model.adblocking.AdBlockMethod.UNDEFINED;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static java.lang.Boolean.TRUE;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.VpnService;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.topjohnwu.superuser.Shell;

import org.adaway.R;
import org.adaway.databinding.WelcomeMethodLayoutBinding;
import org.adaway.helper.PreferenceHelper;
import org.adaway.util.log.SentryLog;

/**
 * This class is a fragment to setup the ad blocking method.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class WelcomeMethodFragment extends WelcomeFragment {
    private WelcomeMethodLayoutBinding binding;
    private ActivityResultLauncher<Intent> prepareVpnLauncher;
    @ColorInt
    private int cardColor;
    @ColorInt
    private int cardEnabledColor;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        this.binding = WelcomeMethodLayoutBinding.inflate(inflater, container, false);
        this.prepareVpnLauncher = registerForActivityResult(new StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                notifyVpnEnabled();
            } else {
                notifyVpnDisabled();
                checkAlwaysOnVpn();
            }
        });

        this.binding.rootCardView.setOnClickListener(this::checkRoot);
        this.binding.vpnCardView.setOnClickListener(this::enableVpnService);

        this.cardColor = MaterialColors.getColor(requireContext(), R.attr.colorSecondaryContainer, Color.GREEN);
        this.cardEnabledColor = MaterialColors.getColor(requireContext(), R.attr.colorPrimaryContainer, Color.GREEN);
        return this.binding.getRoot();
    }

    private void checkRoot(@Nullable View view) {
        notifyVpnDisabled();
        Shell.getShell();
        if (TRUE.equals(Shell.isAppGrantedRoot())) {
            notifyRootEnabled();
        } else {
            notifyRootDisabled(true);
        }
    }

    private void enableVpnService(@Nullable View view) {
        notifyRootDisabled(false);
        Context context = getContext();
        if (context == null) {
            return;
        }
        // Check user authorization
        Intent prepareIntent = VpnService.prepare(context);
        if (prepareIntent == null) {
            notifyVpnEnabled();
        } else {
            this.prepareVpnLauncher.launch(prepareIntent);
        }
    }

    private void notifyRootEnabled() {
        SentryLog.recordBreadcrumb("Enable root ad-blocking method");
        PreferenceHelper.setAbBlockMethod(requireContext(), ROOT);
        this.binding.rootCardView.setCardBackgroundColor(this.cardEnabledColor);
        this.binding.vpnCardView.setCardBackgroundColor(this.cardColor);
        allowNext();
    }

    private void notifyRootDisabled(boolean showDialog) {
        PreferenceHelper.setAbBlockMethod(requireContext(), UNDEFINED);
        this.binding.rootCardView.setCardBackgroundColor(this.cardColor);
        this.binding.vpnCardView.setCardBackgroundColor(this.cardColor);
        if (showDialog) {
            blockNext();
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.welcome_root_missing_title)
                    .setMessage(R.string.welcome_root_missile_description)
                    .setPositiveButton(R.string.button_close, null)
                    .create()
                    .show();
        }
    }

    private void notifyVpnEnabled() {
        SentryLog.recordBreadcrumb("Enable vpn ad-blocking method");
        PreferenceHelper.setAbBlockMethod(requireContext(), VPN);
        this.binding.rootCardView.setCardBackgroundColor(this.cardColor);
        this.binding.vpnCardView.setCardBackgroundColor(this.cardEnabledColor);
        allowNext();
    }

    private void notifyVpnDisabled() {
        PreferenceHelper.setAbBlockMethod(requireContext(), UNDEFINED);
        this.binding.rootCardView.setCardBackgroundColor(this.cardColor);
        this.binding.vpnCardView.setCardBackgroundColor(this.cardColor);
        blockNext();
    }

    private void checkAlwaysOnVpn() {
        @StringRes int alwayson_message = R.string.welcome_vpn_alwayson_description;
        try {
            String alwaysOn = Settings.Secure.getString(requireContext().getContentResolver(), "always_on_vpn_app");
            if (alwaysOn == null) {
                return;
            }
        } catch (SecurityException exception) {
            // Some Android versions will block the always_on_vpn_app request, as it's not marked @Readable
            alwayson_message = R.string.welcome_vpn_alwayson_blocked_description;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.welcome_vpn_alwayson_title)
                .setMessage(alwayson_message)
                .setNegativeButton(R.string.button_close, null)
                .setPositiveButton(
                        R.string.welcome_vpn_alwayson_settings_action,
                        (dialog, which) -> {
                            dialog.dismiss();
                            Intent intent = new Intent(ACTION_VPN_SETTINGS);
                            startActivity(intent);
                        })
                .create()
                .show();
    }
}
