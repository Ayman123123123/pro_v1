package com.red.sovereign.profiles.manage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import org.signal.core.util.concurrent.REDExecutors;
import org.signal.core.util.logging.Log;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.jobs.MultiDeviceProfileContentUpdateJob;
import com.red.sovereign.keyvalue.REDStore;
import com.red.sovereign.profiles.AvatarHelper;
import com.red.sovereign.profiles.ProfileName;
import com.red.sovereign.recipients.Recipient;
import com.red.sovereign.util.ProfileUtil;
import org.whispersystems.signalservice.api.util.StreamDetails;

import java.io.ByteArrayInputStream;
import java.io.IOException;

final class EditProfileRepository {

  private static final String TAG = Log.tag(EditProfileRepository.class);

  public void setName(@NonNull Context context, @NonNull ProfileName profileName, @NonNull Consumer<Result> callback) {
    REDExecutors.UNBOUNDED.execute(() -> {
      try {
        ProfileUtil.uploadProfileWithName(context, profileName);
        REDDatabase.recipients().setProfileName(Recipient.self().getId(), profileName);
        AppDependencies.getJobManager().add(new MultiDeviceProfileContentUpdateJob());

        callback.accept(Result.SUCCESS);
      } catch (IOException e) {
        Log.w(TAG, "Failed to upload profile during name change.", e);
        callback.accept(Result.FAILURE_NETWORK);
      }
    });
  }

  public void setAbout(@NonNull Context context, @NonNull String about, @NonNull String emoji, @NonNull Consumer<Result> callback) {
    REDExecutors.UNBOUNDED.execute(() -> {
      try {
        ProfileUtil.uploadProfileWithAbout(context, about, emoji);
        REDDatabase.recipients().setAbout(Recipient.self().getId(), about, emoji);
        AppDependencies.getJobManager().add(new MultiDeviceProfileContentUpdateJob());

        callback.accept(Result.SUCCESS);
      } catch (IOException e) {
        Log.w(TAG, "Failed to upload profile during about change.", e);
        callback.accept(Result.FAILURE_NETWORK);
      }
    });
  }

  public void setAvatar(@NonNull Context context, @NonNull byte[] data, @NonNull String contentType, @NonNull Consumer<Result> callback) {
    REDExecutors.UNBOUNDED.execute(() -> {
      try {
        ProfileUtil.uploadProfileWithAvatar(new StreamDetails(new ByteArrayInputStream(data), contentType, data.length));
        AvatarHelper.setAvatar(context, Recipient.self().getId(), new ByteArrayInputStream(data));
        REDStore.misc().setHasEverHadAnAvatar(true);
        AppDependencies.getJobManager().add(new MultiDeviceProfileContentUpdateJob());

        callback.accept(Result.SUCCESS);
      } catch (IOException e) {
        Log.w(TAG, "Failed to upload profile during avatar change.", e);
        callback.accept(Result.FAILURE_NETWORK);
      }
    });
  }

  public void clearAvatar(@NonNull Context context, @NonNull Consumer<Result> callback) {
    REDExecutors.UNBOUNDED.execute(() -> {
      try {
        ProfileUtil.uploadProfileWithAvatar(null);
        AvatarHelper.delete(context, Recipient.self().getId());
        AppDependencies.getJobManager().add(new MultiDeviceProfileContentUpdateJob());

        callback.accept(Result.SUCCESS);
      } catch (IOException e) {
        Log.w(TAG, "Failed to upload profile during name change.", e);
        callback.accept(Result.FAILURE_NETWORK);
      }
    });
  }

  enum Result {
    SUCCESS, FAILURE_NETWORK
  }
}
