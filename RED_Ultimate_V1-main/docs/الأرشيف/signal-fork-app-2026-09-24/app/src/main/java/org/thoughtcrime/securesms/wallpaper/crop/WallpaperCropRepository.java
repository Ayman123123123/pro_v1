package com.red.sovereign.wallpaper.crop;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.core.util.logging.Log;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.keyvalue.REDStore;
import com.red.sovereign.recipients.RecipientId;
import com.red.sovereign.wallpaper.ChatWallpaper;
import com.red.sovereign.wallpaper.WallpaperStorage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

final class WallpaperCropRepository {

  private static final String TAG = Log.tag(WallpaperCropRepository.class);

  @Nullable private final RecipientId recipientId;
  private final           Context     context;

  public WallpaperCropRepository(@Nullable RecipientId recipientId) {
    this.context     = AppDependencies.getApplication();
    this.recipientId = recipientId;
  }

  @WorkerThread
  @NonNull ChatWallpaper setWallPaper(byte[] bytes) throws IOException {
    try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
      ChatWallpaper wallpaper = WallpaperStorage.save(inputStream);

      if (recipientId != null) {
        Log.i(TAG, "Setting image wallpaper for " + recipientId);
        REDDatabase.recipients().setWallpaper(recipientId, wallpaper, true);
      } else {
        Log.i(TAG, "Setting image wallpaper for default");
        REDStore.wallpaper().setWallpaper(wallpaper);
      }

      return wallpaper;
    }
  }
}
