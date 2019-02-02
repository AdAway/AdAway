/*
 * Copyright (C) 2011-2012 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This file is part of AdAway.
 *
 * AdAway is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AdAway is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AdAway.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.adaway.ui.tcpdump;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.adaway.R;
import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.sufficientlysecure.rootcommands.Shell;

import java.io.IOException;

/**
 * This class is a fragment to start/stop tcpdump tool and display its log.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class TcpdumpFragment extends Fragment {
    /**
     * The root shell to start and stop tcpdump.
     */
    private Shell mRootShell;
    /**
     * The tcpdump running status ({@code true} if running, {@code false} if not, {@code null} if not defined).
     */
    private Boolean mTcpdumpRunning;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate layout
        View view = inflater.inflate(R.layout.tcpdump_fragment, container, false);
        // Get activity
        final Activity activity = this.getActivity();
        // Create root shell
        try {
            this.mRootShell = Shell.startRootShell();
        } catch (Exception exception) {
            Log.e(Constants.TAG, "Unable to create root shell for tcpdump.", exception);
        }
        // Check if tcpdump if running
        this.mTcpdumpRunning = TcpdumpUtils.isTcpdumpRunning(this.mRootShell);
        /*
         * Configure view.
         */
        // Get tcpdump enable button
        Button tcpdumpEnableButton = view.findViewById(R.id.tcpdump_enable_monitoring);
        // Set tcpdump enable button text according tcpdump running state
        tcpdumpEnableButton.setText(this.mTcpdumpRunning ? R.string.tcpdump_disable_monitoring : R.string.tcpdump_enable_monitoring);
        // Bind tcpdump enable button action listener to start/stop tcpdump
        tcpdumpEnableButton.setOnClickListener(buttonView -> {
            // Check shell
            if (this.mRootShell == null) {
                return;
            }
            // Check button checked state
            if (this.mTcpdumpRunning) {
                // Stop tcpdump
                TcpdumpUtils.stopTcpdump(this.mRootShell);
                // Update tcp running status
                this.mTcpdumpRunning = false;
                // Update button text
                tcpdumpEnableButton.setText(R.string.tcpdump_enable_monitoring);
            } else if (TcpdumpUtils.startTcpdump(activity, this.mRootShell)) {
                // Update tcp running status
                this.mTcpdumpRunning = true;
                // Update button text
                tcpdumpEnableButton.setText(R.string.tcpdump_disable_monitoring);
            }
        });
        // Get open button
        Button openButton = view.findViewById(R.id.tcpdump_show_results);
        // Set open button on click listener to start tcpdump log activity
        openButton.setOnClickListener(
                clickedView -> this.startActivity(new Intent(activity, TcpdumpLogActivity.class))
        );
        // Return created view
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Check if shell is initialized
        if (this.mRootShell != null) {
            try {
                // Close shell
                this.mRootShell.close();
                this.mRootShell = null;
            } catch (IOException exception) {
                Log.e(Constants.TAG, "Unable to close tcpdump shell.", exception);
            }
        }
    }
}
