/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.red.sovereign.util;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.ui.compose.REDIcons;
import com.red.sovereign.R;
import com.red.sovereign.registration.ui.RegistrationActivity;

import java.util.Objects;

public class Dialogs {
  public static void showAlertDialog(Context context, String title, String message) {
    new MaterialAlertDialogBuilder(context)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok, null)
        .show();
  }

  public static void showInfoDialog(Context context, String title, String message) {
    new MaterialAlertDialogBuilder(context)
        .setTitle(title)
        .setMessage(message)
        .setIcon(org.signal.core.ui.R.drawable.symbol_info_24)
        .setPositiveButton(android.R.string.ok, null)
        .show();
  }

  public static void showUpgradeREDDialog(@NonNull Context context) {
    new MaterialAlertDialogBuilder(context)
        .setTitle(R.string.UpdateREDExpiredDialog__title)
        .setMessage(R.string.UpdateREDExpiredDialog__message)
        .setNegativeButton(R.string.UpdateREDExpiredDialog__cancel_action, null)
        .setPositiveButton(R.string.UpdateREDExpiredDialog__update_action, (d, w) -> {
          PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(context);
        })
        .show();
  }

  public static void showReregisterREDDialog(@NonNull Context context) {
    new MaterialAlertDialogBuilder(context)
        .setTitle(R.string.ReregisterREDDialog__title)
        .setMessage(R.string.ReregisterREDDialog__message)
        .setNegativeButton(R.string.ReregisterREDDialog__cancel_action, null)
        .setPositiveButton(R.string.ReregisterREDDialog__reregister_action, (d, w) -> {
          context.startActivity(RegistrationActivity.newIntentForReRegistration(context));
        })
        .show();
  }
}
