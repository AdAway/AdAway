package org.adaway.ui.home;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProviders;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.hostsinstall.HostsInstallError;
import org.adaway.model.hostsinstall.HostsInstallStatus;
import org.adaway.ui.help.HelpActivity;
import org.adaway.util.WebServerUtils;
import org.adaway.vpn.VpnService;

import static android.app.Activity.RESULT_OK;
import static org.adaway.model.hostsinstall.HostsInstallStatus.WORK_IN_PROGRESS;

/**
 * This class is a {@link Fragment} to show home cards:
 * <ul>
 * <li>welcome card</li>
 * <li>hosts install/update/revert</li>
 * <li>VPN service</li>
 * <li>web sever</li>
 * </ul>
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class HomeFragment extends Fragment {
    /*
     * Activity action.
     */
    /**
     * The activity request/result code for starting VPN service.
     */
    private static final int VPN_START_REQUEST_CODE = 10;
    /*
     * State save.
     */
    /**
     * The state of the web server ({@code true} if running, {@code false} otherwise).
     */
    private static final String STATE_WEB_SERVER_RUNNING = "webServerRunning";
    /**
     * The state of the current error code ({@code null} if no error).
     */
    private static final String STATE_CURRENT_ERROR = "currentError";
    /*
     * Current statuses.
     */
    /**
     * The fragment view model ({@code null} until view is created).
     */
    private HostsInstallViewModel mViewModel;
    /**
     * The current hosts file installation status ({@code null} if not initialized).
     */
    private HostsInstallStatus mCurrentStatus;
    /**
     * The current error code ({@code null} if no previous error).
     */
    private HostsInstallError mCurrentError;
    /**
     * The web server running status (<code>true</code> if running, <code>false</code> otherwise).
     */
    private boolean mWebServerRunning = false;
    /*
     * Status card views.
     */
    /**
     * The status progress bar (<code>null</code> until view created).
     */
    private ProgressBar mStatusProgressBar;
    /**
     * The status icon image view (<code>null</code> until view created).
     */
    private ImageView mStatusIconImageView;
    /**
     * The status title text view (<code>null</code> until view created).
     */
    private TextView mStatusTitleTextView;
    /**
     * The status title text view (<code>null</code> until view created).
     */
    private TextView mStatusTextView;
    /**
     * The update hosts button (<code>null</code> until view created).
     */
    private Button mUpdateHostsButton;
    /**
     * The revert hosts button (<code>null</code> until view created).
     */
    private Button mRevertHostsButton;
    /*
     * VPN service card views.
     */
    /**
     * The VPN service status text(<code>null</code> until view created).
     */
    private TextView mVpnServiceStatusTextView;
    /**
     * The VPN service status icon(<code>null</code> until view created).
     */
    private ImageView mVpnServiceStatusImageView;
    /**
     * The enable/disable VPN service button (<code>null</code> until view created).
     */
    private Button mRunningVpnServiceButton;
    /*
     * Web server card views.
     */
    /**
     * The web server status text(<code>null</code> until view created).
     */
    private TextView mWebServerStatusTextView;
    /**
     * The web server status icon(<code>null</code> until view created).
     */
    private ImageView mWebServerStatusImageView;
    /**
     * The enable/disable web server button (<code>null</code> until view created).
     */
    private Button mRunningWebServerButton;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Get fragment context
        Context context = this.getContext();
        // Inflate layout
        View view = inflater.inflate(R.layout.home_fragment, container, false);
        /*
         * Retrieve view elements.
         */
        // Get view from help card
        CardView welcomeCardView = view.findViewById(R.id.home_welcome_card);
        Button showHelpButton = view.findViewById(R.id.home_show_help);
        // Get views from status card
        mStatusProgressBar = view.findViewById(R.id.home_status_progress);
        mStatusIconImageView = view.findViewById(R.id.home_status_icon);
        mStatusTitleTextView = view.findViewById(R.id.home_status_title);
        mStatusTextView = view.findViewById(R.id.home_status_text);
        mUpdateHostsButton = view.findViewById(R.id.home_update_hosts);
        mRevertHostsButton = view.findViewById(R.id.home_revert_hosts);
        // Get view from VNP card
        mVpnServiceStatusTextView = view.findViewById(R.id.home_vpn_status);
        mVpnServiceStatusImageView = view.findViewById(R.id.home_vpn_icon);
        mRunningVpnServiceButton = view.findViewById(R.id.home_vpn_enable);
        // Get views from web server card
        CardView webServerCardView = view.findViewById(R.id.home_webserver_card);
        mWebServerStatusTextView = view.findViewById(R.id.home_webserver_status);
        mWebServerStatusImageView = view.findViewById(R.id.home_webserver_icon);
        mRunningWebServerButton = view.findViewById(R.id.home_webserver_enable);
        /*
         * Initialize and bind to view model.
         */
        // Get the model scope
        FragmentActivity activity = this.getActivity();
        if (activity != null) {
            // Get the model
            mViewModel = ViewModelProviders.of(activity).get(HostsInstallViewModel.class);
            // Bind model to views
            mViewModel.getStatus().observe(this, status -> {
                if (status == null) {
                    return;
                }
                switch (status) {
                    case INSTALLED:
                        mStatusProgressBar.setVisibility(View.GONE);
                        mStatusIconImageView.setVisibility(View.VISIBLE);
                        mStatusIconImageView.setImageResource(R.drawable.status_enabled);
                        mUpdateHostsButton.setText(R.string.button_check_update_hosts);
                        mRevertHostsButton.setVisibility(View.VISIBLE);
                        break;
                    case OUTDATED:
                        mStatusProgressBar.setVisibility(View.GONE);
                        mStatusIconImageView.setVisibility(View.VISIBLE);
                        mStatusIconImageView.setImageResource(R.drawable.status_update);
                        mUpdateHostsButton.setText(R.string.button_update_hosts);
                        mRevertHostsButton.setVisibility(View.VISIBLE);
                        break;
                    case ORIGINAL:
                        mStatusProgressBar.setVisibility(View.GONE);
                        mStatusIconImageView.setVisibility(View.VISIBLE);
                        mStatusIconImageView.setImageResource(R.drawable.status_disabled);
                        mUpdateHostsButton.setText(R.string.button_enable_hosts);
                        mRevertHostsButton.setVisibility(View.GONE);
                        break;
                    case WORK_IN_PROGRESS:
                        mStatusProgressBar.setVisibility(View.VISIBLE);
                        mStatusIconImageView.setVisibility(View.GONE);
                }
                // Update button enable state
                boolean enabledButton = status != WORK_IN_PROGRESS;
                mUpdateHostsButton.setEnabled(enabledButton);
                mRevertHostsButton.setEnabled(enabledButton);
                // Check status change
                if (mCurrentStatus != null && mCurrentStatus != status) {
                    // Show reboot dialog
                    HostsInstallDialog.showRebootDialog(context, status);
                }
                // Save any final status
                if (status != WORK_IN_PROGRESS) {
                    mCurrentStatus = status;
                }
            });
            mViewModel.getState().observe(this, state -> {
                if (state != null) {
                    mStatusTitleTextView.setText(state);
                }
            });
            mViewModel.getDetails().observe(this, details -> {
                if (details != null) {
                    mStatusTextView.setText(details);
                }
            });
            mViewModel.getError().observe(this, error -> {
                if (error != null) {
                    mStatusProgressBar.setVisibility(View.GONE);
                    mStatusIconImageView.setVisibility(View.VISIBLE);
                    mStatusIconImageView.setImageResource(R.drawable.status_fail);
                    int state;
                    int statusText;
                    switch (error) {
                        case NO_CONNECTION:
                            state = R.string.no_connection_title;
                            statusText = R.string.no_connection;
                            break;
                        case DOWNLOAD_FAIL:
                            state = R.string.status_download_fail;
                            statusText = R.string.status_download_fail_subtitle_new;
                            break;
                        case ROOT_ACCESS_DENIED:
                            state = R.string.status_root_access_denied;
                            statusText = R.string.status_root_access_denied_subtitle;
                            break;
                        default:
                            state = R.string.status_failure;
                            statusText = R.string.status_failure_subtitle;
                            break;
                    }
                    mStatusTitleTextView.setText(state);
                    mStatusTextView.setText(statusText);
                    mUpdateHostsButton.setEnabled(true);
                    mRevertHostsButton.setEnabled(true);
                    if (mCurrentError != error) {
                        mCurrentError = error;
                        HostsInstallDialog.showDialogBasedOnResult(context, error);
                    }
                }
            });
            // Initialize model state
            mViewModel.load();
        }
        /*
         * Initialize statuses and behaviors.
         */
        // Update welcome card visibility
        boolean welcomeCardVisible = context == null || !PreferenceHelper.getDismissWelcome(context);
        if (!welcomeCardVisible) {
            welcomeCardView.setVisibility(View.GONE);
        }
        // Set show help button click listener
        showHelpButton.setOnClickListener(this::showMoreHelp);
        // Set update hosts button click listener
        mUpdateHostsButton.setOnClickListener(this::updateHosts);
        // Set revert hosts button click listener
        mRevertHostsButton.setOnClickListener(this::revertHosts);
        // Set VPN button click listener
        mRunningVpnServiceButton.setOnClickListener(this::toggleVpnService);
        // Update web server card visibility
        boolean webServerCardVisible = context != null && PreferenceHelper.getWebServerEnabled(context);
        if (!webServerCardVisible) {
            webServerCardView.setVisibility(View.GONE);
        }
        // Set running web server button click listener
        mRunningWebServerButton.setOnClickListener(this::toggleWebServer);
        // Update VPN service status
        this.notifyVpnServiceRunning(VpnService.isStarted(context));
        // Check statuses to restore
        if (savedInstanceState == null) {
            // Check web server status
            if (webServerCardVisible) {
                new UpdateWebServerStatusAsyncTask(this).execute();
            }
        } else {
            // Restore states
            String currentError = savedInstanceState.getString(STATE_CURRENT_ERROR);
            if (currentError != null) {
                mCurrentError = HostsInstallError.valueOf(currentError);
            }
            boolean webServerRunning = savedInstanceState.getBoolean(STATE_WEB_SERVER_RUNNING);
            this.notifyWebServerRunning(webServerRunning);
        }
        // Return inflated view
        return view;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_WEB_SERVER_RUNNING, mWebServerRunning);
        if (mCurrentError != null) {
            outState.putString(STATE_CURRENT_ERROR, mCurrentError.name());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mViewModel.getStatus().removeObservers(this);
        mViewModel.getState().removeObservers(this);
        mViewModel.getDetails().removeObservers(this);
        mViewModel.getError().removeObservers(this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Check VPN activation request
        if (requestCode == VPN_START_REQUEST_CODE && resultCode == RESULT_OK) {
            // Start VPN service
            VpnService.start(this.getContext());
            this.notifyVpnServiceRunning(true);
        } // TODO Add toast if user cancels
    }

    /**
     * Button Action to download and apply hosts files
     *
     * @param view The view which trigger the action.
     */
    private void updateHosts(@SuppressWarnings("unused") @Nullable View view) {
        if (mCurrentStatus == null) {
            return;
        }
        // Reset error code
        mCurrentError = null;
        switch (mCurrentStatus) {
            case OUTDATED:
            case ORIGINAL:
                mViewModel.update();
                break;
            case INSTALLED:
                mViewModel.checkForUpdate();
                break;
            default:
                // Nothing to do
                break;
        }
    }

    /**
     * Revert to default hosts file.
     *
     * @param view The view which trigger the action.
     */
    private void revertHosts(@SuppressWarnings("unused") @Nullable View view) {
        // Reset error code
        mCurrentError = null;
        mViewModel.revert();
    }

    /**
     * Show more help.
     *
     * @param view The view which trigger the action.
     */
    private void showMoreHelp(@SuppressWarnings("unused") @Nullable View view) {
        // Start help activity
        this.startActivity(new Intent(this.getActivity(), HelpActivity.class));
    }

    /**
     * Toggle VPN service running.
     *
     * @param view The view which trigger the action.
     */
    private void toggleVpnService(@SuppressWarnings("unused") @Nullable View view) {
        FragmentActivity activity = this.getActivity();
        if (activity == null) {
            return;
        }
        if (VpnService.isStarted(activity)) {
            // Stop VPN service
            VpnService.stop(activity);
            this.notifyVpnServiceRunning(false);
        } else {
            // Check user authorization
            Intent prepareIntent = android.net.VpnService.prepare(activity);
            if (prepareIntent != null) {
                startActivityForResult(prepareIntent, VPN_START_REQUEST_CODE);
                return;
            }
            // Start VPN service
            VpnService.start(activity);
            this.notifyVpnServiceRunning(true);
        }
    }

    /**
     * Toggle web server running.
     *
     * @param view The view which trigger the action.
     */
    private void toggleWebServer(@SuppressWarnings("unused") @Nullable View view) {
        if (mWebServerRunning) {
            WebServerUtils.stopWebServer();
        } else {
            WebServerUtils.startWebServer(this.getContext());
        }
        this.notifyWebServerRunning(!mWebServerRunning);
    }

    /**
     * Notify the VPN service is running.
     *
     * @param running <code>true</code> if the VPN service is running, <code>false</code> otherwise.
     */
    private void notifyVpnServiceRunning(boolean running) {
        // Check button
        if (mVpnServiceStatusTextView == null || mWebServerStatusImageView == null || mRunningWebServerButton == null) {
            return;
        }
        // Update status text and icon
        mVpnServiceStatusTextView.setText(running ?
                R.string.vpn_status_running :
                R.string.vpn_status_stopped
        );
        mVpnServiceStatusImageView.setImageResource(running ?
                R.drawable.status_enabled :
                R.drawable.status_disabled
        );
        // Update button text
        mRunningVpnServiceButton.setText(running ?
                R.string.button_disable_vpn :
                R.string.button_enable_vpn
        );
    }

    /**
     * Notify the web server is running.
     *
     * @param running <code>true</code> if the web server is running, <code>false</code> otherwise.
     */
    void notifyWebServerRunning(boolean running) {
        // Check button
        if (mWebServerStatusTextView == null || mWebServerStatusImageView == null || mRunningWebServerButton == null) {
            return;
        }
        // Store web server running status
        mWebServerRunning = running;
        // Update status text and icon
        mWebServerStatusTextView.setText(running ?
                R.string.webserver_status_running :
                R.string.webserver_status_stopped
        );
        mWebServerStatusImageView.setImageResource(running ?
                R.drawable.status_enabled :
                R.drawable.status_disabled
        );
        // Update button text
        mRunningWebServerButton.setText(running ?
                R.string.button_disable_webserver :
                R.string.button_enable_webserver
        );
    }
}
