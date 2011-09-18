/*
 * Copyright (C) 2011 Dominik Schürmann <dominik@dominikschuermann.de>
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

package org.adaway.helper;

import java.io.FileOutputStream;

import org.adaway.R;
import org.adaway.ui.BaseFragment;
import org.adaway.util.ApplyUtils;
import org.adaway.util.CommandException;
import org.adaway.util.Constants;
import org.adaway.util.NotEnoughSpaceException;
import org.adaway.util.RemountException;
import org.adaway.util.Utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;

public class RevertExecutor {
    private BaseFragment mBaseFragment;
    private Activity mActivity;

    /**
     * Constructor based on fragment
     * 
     * @param baseFragment
     */
    public RevertExecutor(BaseFragment baseFragment) {
        super();
        this.mBaseFragment = baseFragment;
        this.mActivity = baseFragment.getActivity();
    }

    public void revert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setTitle(R.string.button_revert);
        builder.setMessage(mActivity.getString(R.string.revert_question));
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setCancelable(false);
        builder.setPositiveButton(mActivity.getString(R.string.button_yes),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // build standard hosts file
                        try {
                            FileOutputStream fos = mActivity.openFileOutput(
                                    Constants.HOSTS_FILENAME, Context.MODE_PRIVATE);

                            // default localhost
                            String localhost = Constants.LOCALHOST_IPv4 + " "
                                    + Constants.LOCALHOST_HOSTNAME;
                            fos.write(localhost.getBytes());
                            fos.close();

                            // copy build hosts file with RootTools
                            try {
                                ApplyUtils.copyHostsFile(mActivity, false);
                            } catch (NotEnoughSpaceException e) {
                                Log.e(Constants.TAG, "Exception: " + e);
                                e.printStackTrace();

                                throw new Exception(); // TODO: make it better
                            } catch (RemountException e) {
                                Log.e(Constants.TAG, "Exception: " + e);
                                e.printStackTrace();

                                throw new Exception(); // TODO: make it better
                            } catch (CommandException e) {
                                Log.e(Constants.TAG, "Exception: " + e);
                                e.printStackTrace();

                                throw new Exception(); // TODO: make it better
                            }

                            // delete generated hosts file after applying it
                            mActivity.deleteFile(Constants.HOSTS_FILENAME);

                            // set status to disabled
                            mBaseFragment.setStatusDisabled();

                            Utils.rebootQuestion(mActivity, R.string.revert_successful_title,
                                    R.string.revert_successful);
                        } catch (Exception e) {
                            Log.e(Constants.TAG, "Exception: " + e);
                            e.printStackTrace();

                            AlertDialog alertDialog = new AlertDialog.Builder(mActivity).create();
                            alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
                            alertDialog.setTitle(R.string.button_revert);
                            alertDialog.setMessage(mActivity
                                    .getString(org.adaway.R.string.revert_problem));
                            alertDialog.setButton(mActivity.getString(R.string.button_close),
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dlg, int sum) {
                                            dlg.dismiss();
                                        }
                                    });
                            alertDialog.show();
                        }

                    }
                });
        builder.setNegativeButton(mActivity.getString(R.string.button_no),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog question = builder.create();
        question.show();
    }
}
