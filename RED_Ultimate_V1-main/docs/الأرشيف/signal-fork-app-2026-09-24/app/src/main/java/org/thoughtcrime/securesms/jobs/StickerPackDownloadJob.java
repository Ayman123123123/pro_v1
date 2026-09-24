package com.red.sovereign.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Preconditions;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.InvalidMessageException;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.database.StickerTables;
import com.red.sovereign.database.model.IncomingSticker;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.jobmanager.JsonJobData;
import com.red.sovereign.jobmanager.Job;
import com.red.sovereign.jobmanager.JobManager;
import com.red.sovereign.jobmanager.impl.DataRestoreConstraint;
import com.red.sovereign.jobmanager.impl.NetworkConstraint;
import com.red.sovereign.stickers.BlessedPacks;
import org.signal.core.util.Hex;
import org.whispersystems.signalservice.api.REDServiceMessageReceiver;
import org.whispersystems.signalservice.api.messages.REDServiceStickerManifest;
import org.whispersystems.signalservice.api.messages.REDServiceStickerManifest.StickerInfo;
import org.signal.network.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class StickerPackDownloadJob extends BaseJob {

  public static final String KEY = "StickerPackDownloadJob";

  private static final String TAG = Log.tag(StickerPackDownloadJob.class);

  private static final int MAX_STICKERS_PER_PACK = 1024;

  private static final String KEY_PACK_ID        = "pack_key";
  private static final String KEY_PACK_KEY       = "pack_id";
  private static final String KEY_REFERENCE_PACK = "reference_pack";
  private static final String KEY_NOTIFY         = "notify";

  private final String  packId;
  private final String  packKey;
  private final boolean isReferencePack;
  private final boolean notify;

  /**
   * Downloads all the stickers in a pack.
   * @param notify Whether or not a tooltip will be shown indicating the pack was installed.
   */
  public static @NonNull StickerPackDownloadJob forInstall(@NonNull String packId, @NonNull String packKey, boolean notify) {
    return new StickerPackDownloadJob(packId, packKey, false, notify);
  }

  /**
   * Just installs a reference to the pack -- i.e. just the cover.
   */
  public static @NonNull StickerPackDownloadJob forReference(@NonNull String packId, @NonNull String packKey) {
    return new StickerPackDownloadJob(packId, packKey, true, true);
  }

  private StickerPackDownloadJob(@NonNull String packId, @NonNull String packKey, boolean isReferencePack, boolean notify)
  {
    this(new Parameters.Builder()
                       .addConstraint(NetworkConstraint.KEY)
                       .addConstraint(DataRestoreConstraint.KEY)
                       .setLifespan(TimeUnit.DAYS.toMillis(30))
                       .setQueue("StickerPackDownloadJob_" + packId)
                       .build(),
        packId,
        packKey,
        isReferencePack,
        notify);
  }

  private StickerPackDownloadJob(@NonNull Parameters parameters,
                                 @NonNull String packId,
                                 @NonNull String packKey,
                                 boolean isReferencePack,
                                 boolean notify)
  {
    super(parameters);

    Preconditions.checkNotNull(packId);
    Preconditions.checkNotNull(packKey);

    this.packId          = packId;
    this.packKey         = packKey;
    this.isReferencePack = isReferencePack;
    this.notify          = notify;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putString(KEY_PACK_ID, packId)
                                    .putString(KEY_PACK_KEY, packKey)
                                    .putBoolean(KEY_REFERENCE_PACK, isReferencePack)
                                    .putBoolean(KEY_NOTIFY, notify)
                                    .serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws IOException, InvalidMessageException {
    if (isReferencePack && !REDDatabase.attachments().containsStickerPackId(packId) && !BlessedPacks.contains(packId)) {
      Log.w(TAG, "There are no attachments with the requested packId present for this reference pack. Skipping.");
      return;
    }

    if (isReferencePack && REDDatabase.stickers().isPackAvailableAsReference(packId)) {
      Log.i(TAG, "Sticker pack already available for reference. Skipping.");
      return;
    }

    REDServiceMessageReceiver receiver        = AppDependencies.getREDServiceMessageReceiver();
    JobManager    jobManager      = AppDependencies.getJobManager();
    StickerTables stickerDatabase = REDDatabase.stickers();
    byte[]        packIdBytes     = Hex.fromStringCondensed(packId);
    byte[]                       packKeyBytes    = Hex.fromStringCondensed(packKey);
    REDServiceStickerManifest manifest        = receiver.retrieveStickerManifest(packIdBytes, packKeyBytes);

    if (manifest.getStickers().isEmpty()) {
      Log.w(TAG, "No stickers in pack!");
      return;
    }

    List<StickerInfo> stickers = manifest.getStickers();
    if (stickers.size() > MAX_STICKERS_PER_PACK) {
      Log.w(TAG, "Pack manifest contains " + stickers.size() + " stickers, which exceeds the cap of " + MAX_STICKERS_PER_PACK + ". Truncating.");
      stickers = stickers.subList(0, MAX_STICKERS_PER_PACK);
    }

    if (!isReferencePack && stickerDatabase.isPackAvailableAsReference(packId)) {
      stickerDatabase.markPackAsInstalled(packId, notify);
    }

    StickerInfo      cover = manifest.getCover().orElse(stickers.get(0));
    JobManager.Chain chain = jobManager.startChain(new StickerDownloadJob(new IncomingSticker(packId,
                                                                                              packKey,
                                                                                              manifest.getTitle().orElse(""),
                                                                                              manifest.getAuthor().orElse(""),
                                                                                              cover.getId(),
                                                                                              "",
                                                                                              cover.getContentType(),
                                                                                              true,
                                                                                              !isReferencePack),
                                                                          notify));



    if (!isReferencePack) {
      List<Job> jobs = new ArrayList<>(stickers.size());

      for (StickerInfo stickerInfo : stickers) {
        jobs.add(new StickerDownloadJob(new IncomingSticker(packId,
                                                            packKey,
                                                            manifest.getTitle().orElse(""),
                                                            manifest.getAuthor().orElse(""),
                                                            stickerInfo.getId(),
                                                            stickerInfo.getEmoji(),
                                                            stickerInfo.getContentType(),
                                                            false,
                                                            true),
                                        notify));
      }

      chain.then(jobs);
    }

    chain.enqueue();
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof PushNetworkException;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "Failed to download manifest! Uninstalling pack.");
    REDDatabase.stickers().uninstallPack(packId);
    REDDatabase.stickers().deleteOrphanedPacks();
  }

  public static final class Factory implements Job.Factory<StickerPackDownloadJob> {
    @Override
    public @NonNull StickerPackDownloadJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      return new StickerPackDownloadJob(parameters,
                                        data.getString(KEY_PACK_ID),
                                        data.getString(KEY_PACK_KEY),
                                        data.getBoolean(KEY_REFERENCE_PACK),
                                        data.getBoolean(KEY_NOTIFY));
    }
  }
}
