package com.red.sovereign.stickers.preview;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.util.stream.Collectors;

import org.signal.core.util.concurrent.REDExecutors;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.InvalidMessageException;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.database.StickerTables;
import com.red.sovereign.database.model.StickerPackRecord;
import com.red.sovereign.database.model.StickerRecord;
import com.red.sovereign.dependencies.AppDependencies;
import org.signal.core.util.Hex;
import com.red.sovereign.stickers.StickerManifest;
import org.whispersystems.signalservice.api.REDServiceMessageReceiver;
import org.whispersystems.signalservice.api.messages.REDServiceStickerManifest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class StickerPackPreviewRepository {

  private static final String TAG = Log.tag(StickerPackPreviewRepository.class);

  private final StickerTables                stickerDatabase;
  private final REDServiceMessageReceiver receiver;

  public StickerPackPreviewRepository() {
    this.receiver        = AppDependencies.getREDServiceMessageReceiver();
    this.stickerDatabase = REDDatabase.stickers();
  }

  public void getStickerManifest(@NonNull String packId,
                                 @NonNull String packKey,
                                 @NonNull Callback<Optional<StickerManifestResult>> callback)
  {
    REDExecutors.UNBOUNDED.execute(() -> {
      Optional<StickerManifestResult> localManifest = getManifestFromDatabase(packId);

      if (localManifest.isPresent()) {
        Log.d(TAG, "Found manifest locally.");
        callback.onComplete(localManifest);
      } else {
        Log.d(TAG, "Looking for manifest remotely.");
        callback.onComplete(getManifestRemote(packId, packKey));
      }
    });
  }

  @WorkerThread
  private Optional<StickerManifestResult> getManifestFromDatabase(@NonNull String packId) {
    StickerPackRecord record = stickerDatabase.getStickerPack(packId);

    if (record != null && record.isInstalled) {
      StickerManifest.Sticker       cover    = toSticker(record.cover);
      List<StickerManifest.Sticker> stickers = getStickersFromDatabase(packId);

      StickerManifest manifest = new StickerManifest(record.packId,
                                                     record.packKey,
                                                     record.titleOptional,
                                                     record.authorOptional,
                                                     Optional.of(cover),
                                                     stickers);

      return Optional.of(new StickerManifestResult(manifest, record.isInstalled));
    }

    return Optional.empty();
  }

  @WorkerThread
  private Optional<StickerManifestResult> getManifestRemote(@NonNull String packId, @NonNull String packKey) {
    try {
      byte[]                       packIdBytes    = Hex.fromStringCondensed(packId);
      byte[]                       packKeyBytes   = Hex.fromStringCondensed(packKey);
      REDServiceStickerManifest remoteManifest = receiver.retrieveStickerManifest(packIdBytes, packKeyBytes);
      StickerManifest              localManifest  = new StickerManifest(packId,
                                                                        packKey,
                                                                        remoteManifest.getTitle(),
                                                                        remoteManifest.getAuthor(),
                                                                        toOptionalSticker(packId, packKey, remoteManifest.getCover()),
                                                                        remoteManifest.getStickers().stream()
                                                                                      .map(s -> toSticker(packId, packKey, s)).collect(Collectors.toList()));

      return Optional.of(new StickerManifestResult(localManifest, false));
    } catch (IOException | InvalidMessageException e) {
      Log.w(TAG, "Failed to retrieve pack manifest.", e);
    }

    return Optional.empty();
  }

  @WorkerThread
  private List<StickerManifest.Sticker> getStickersFromDatabase(@NonNull String packId) {
    List<StickerManifest.Sticker> stickers = new ArrayList<>();

    try (Cursor cursor = stickerDatabase.getStickersForPack(packId)) {
      StickerTables.StickerRecordReader reader = new StickerTables.StickerRecordReader(cursor);

      StickerRecord record;
      while ((record = reader.getNext()) != null) {
        stickers.add(toSticker(record));
      }
    }

    return stickers;
  }


  private Optional<StickerManifest.Sticker> toOptionalSticker(@NonNull String packId,
                                                              @NonNull String packKey,
                                                              @NonNull Optional<REDServiceStickerManifest.StickerInfo> remoteSticker)
  {
    return remoteSticker.isPresent() ? Optional.of(toSticker(packId, packKey, remoteSticker.get()))
                                     : Optional.empty();
  }

  private StickerManifest.Sticker toSticker(@NonNull String packId,
                                            @NonNull String packKey,
                                            @NonNull REDServiceStickerManifest.StickerInfo remoteSticker)
  {
    return new StickerManifest.Sticker(packId, packKey, remoteSticker.getId(), remoteSticker.getEmoji(), remoteSticker.getContentType());
  }

  private StickerManifest.Sticker toSticker(@NonNull StickerRecord record) {
    return new StickerManifest.Sticker(record.packId, record.packKey, record.stickerId, record.emoji, record.contentType, record.uri);
  }

  static class StickerManifestResult {
    private final StickerManifest manifest;
    private final boolean         isInstalled;

    StickerManifestResult(StickerManifest manifest, boolean isInstalled) {
      this.manifest    = manifest;
      this.isInstalled = isInstalled;
    }

    public StickerManifest getManifest() {
      return manifest;
    }

    public boolean isInstalled() {
      return isInstalled;
    }
  }

  interface Callback<T> {
    void onComplete(T result);
  }
}
