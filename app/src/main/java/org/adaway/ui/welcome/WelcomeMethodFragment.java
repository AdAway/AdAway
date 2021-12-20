package org.adaway.ui.welcome;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.topjohnwu.superuser.Shell;

import org.adaway.R;
import org.adaway.databinding.WelcomeMethodLayoutBinding;
import org.adaway.helper.PreferenceHelper;
import org.adaway.util.log.SentryLog;

import static android.app.Activity.RESULT_OK;
import static org.adaway.model.adblocking.AdBlockMethod.ROOT;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;

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
            }
        });

        this.binding.rootCardView.setOnClickListener(this::checkRoot);
        this.binding.vpnCardView.setOnClickListener(this::enableVpnService);

        this.cardColor = getResources().getColor(R.color.cardBackground, null);
        this.cardEnabledColor = getResources().getColor(R.color.cardEnabledBackground, null);
        return this.binding.getRoot();
    }

    private void checkRoot(@Nullable View view) {
        notifyVpnDisabled();
        if (Shell.rootAccess()) {
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
        this.binding.rootCardView.setCardBackgroundColor(this.cardColor);
        this.binding.vpnCardView.setCardBackgroundColor(this.cardColor);
        Context context = getContext();
        if (context != null && showDialog) {
            blockNext();
            new MaterialAlertDialogBuilder(context)
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
        this.binding.rootCardView.setCardBackgroundColor(this.cardColor);
        this.binding.vpnCardView.setCardBackgroundColor(this.cardColor);
        blockNext();
    }
}
