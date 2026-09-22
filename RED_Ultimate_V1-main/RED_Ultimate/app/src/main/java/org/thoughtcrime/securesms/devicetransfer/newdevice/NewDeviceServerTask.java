package com.red.sovereign.devicetransfer.newdevice;

import android.content.Context;

import androidx.annotation.NonNull;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.util.crypto.AttachmentSecretProvider;
import org.signal.core.util.logging.Log;
import org.signal.devicetransfer.NewDeviceRestoreStatus;
import org.signal.devicetransfer.ServerTask;
import com.red.sovereign.AppInitialization;
import com.red.sovereign.backup.BackupEvent;
import com.red.sovereign.backup.BackupPassphrase;
import com.red.sovereign.backup.FullBackupImporter;
import com.red.sovereign.crypto.AppAttachmentSecretStore;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.jobmanager.impl.DataRestoreConstraint;
import com.red.sovereign.keyvalue.REDStore;
import com.red.sovereign.notifications.NotificationChannels;

import java.io.IOException;
import java.io.InputStream;

/**
 * Performs the restore with the backup data coming in over the input stream. Used in
 * conjunction with {@link org.signal.devicetransfer.DeviceToDeviceTransferService}.
 */
public final class NewDeviceServerTask implements ServerTask {

  public NewDeviceServerTask() {}


  private static final String TAG = Log.tag(NewDeviceServerTask.class);

  @Override
  public void run(@NonNull Context context, @NonNull InputStream inputStream) {
    long start = System.currentTimeMillis();

    Log.i(TAG, "Starting backup restore.");

    EventBus.getDefault().register(this);
    try {
      DataRestoreConstraint.setRestoringData(true);
      SQLiteDatabase database = REDDatabase.getBackupDatabase();

      String passphrase = REDStore.account().getAccountEntropyPool().getValue();

      BackupPassphrase.set(context, passphrase);
      FullBackupImporter.importFile(context,
                                    AttachmentSecretProvider.getInstance(context, AppAttachmentSecretStore.INSTANCE).getOrCreateAttachmentSecret(),
                                    database,
                                    inputStream,
                                    passphrase,
                                    true);

      REDDatabase.runPostBackupRestoreTasks(database);
      NotificationChannels.getInstance().restoreContactNotificationChannels();

      AppInitialization.onPostBackupRestore(context);

      Log.i(TAG, "Backup restore complete.");
    } catch (FullBackupImporter.DatabaseDowngradeException e) {
      Log.w(TAG, "Failed due to the backup being from a newer version of RED.", e);
      EventBus.getDefault().post(new NewDeviceRestoreStatus(0, NewDeviceRestoreStatus.State.FAILURE_VERSION_DOWNGRADE));
    } catch (FullBackupImporter.ForeignKeyViolationException e) {
      Log.w(TAG, "Failed due to foreign key constraint violations.", e);
      EventBus.getDefault().post(new NewDeviceRestoreStatus(0, NewDeviceRestoreStatus.State.FAILURE_FOREIGN_KEY));
    } catch (IOException e) {
      Log.w(TAG, e);
      EventBus.getDefault().post(new NewDeviceRestoreStatus(0, NewDeviceRestoreStatus.State.FAILURE_UNKNOWN));
    } finally {
      EventBus.getDefault().unregister(this);
      DataRestoreConstraint.setRestoringData(false);
    }

    long end = System.currentTimeMillis();
    Log.i(TAG, "Receive took: " + (end - start));

    EventBus.getDefault().post(new NewDeviceRestoreStatus(0, NewDeviceRestoreStatus.State.RESTORE_COMPLETE));
  }

  @Subscribe(threadMode = ThreadMode.POSTING)
  public void onEvent(BackupEvent event) {
    if (event.getType() == BackupEvent.Type.PROGRESS) {
      EventBus.getDefault().post(new NewDeviceRestoreStatus(event.getCount(), NewDeviceRestoreStatus.State.IN_PROGRESS));
    } else if (event.getType() == BackupEvent.Type.FINISHED) {
      EventBus.getDefault().post(new NewDeviceRestoreStatus(event.getCount(), NewDeviceRestoreStatus.State.TRANSFER_COMPLETE));
    }
  }

}
