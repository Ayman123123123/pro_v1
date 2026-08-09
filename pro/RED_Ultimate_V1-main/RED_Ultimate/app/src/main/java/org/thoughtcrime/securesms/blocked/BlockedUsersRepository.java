package com.red.sovereign.blocked;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import org.signal.core.util.concurrent.REDExecutors;
import org.signal.core.util.logging.Log;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.database.model.RecipientRecord;
import com.red.sovereign.groups.GroupChangeBusyException;
import com.red.sovereign.groups.GroupChangeFailedException;
import com.red.sovereign.recipients.Recipient;
import com.red.sovereign.recipients.RecipientId;
import com.red.sovereign.recipients.RecipientUtil;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

class BlockedUsersRepository {

  private static final String TAG = Log.tag(BlockedUsersRepository.class);

  private final Context context;

  BlockedUsersRepository(@NonNull Context context) {
    this.context = context;
  }

  void getBlocked(@NonNull Consumer<List<Recipient>> blockedUsers) {
    REDExecutors.BOUNDED.execute(() -> {
      List<RecipientRecord> records    = REDDatabase.recipients().getBlocked();
      List<Recipient>       recipients = records.stream()
                                                .map((record) -> Recipient.resolved(record.getId()))
                                                .collect(Collectors.toList());
      blockedUsers.accept(recipients);
    });
  }

  void block(@NonNull RecipientId recipientId, @NonNull Runnable success, @NonNull Runnable failure) {
    REDExecutors.BOUNDED.execute(() -> {
      try {
        RecipientUtil.block(context, Recipient.resolved(recipientId));
        success.run();
      } catch (IOException | GroupChangeFailedException | GroupChangeBusyException e) {
        Log.w(TAG, "block: failed to block recipient: ", e);
        failure.run();
      }
    });
  }

  void createAndBlock(@NonNull String number, @NonNull Runnable success) {
    REDExecutors.BOUNDED.execute(() -> {
      Recipient recipient = Recipient.external(number);
      if (recipient != null) {
        RecipientUtil.blockNonGroup(context, recipient);
      } else {
        Log.w(TAG, "Failed to create Recipient for number! Invalid input.");
      }
      success.run();
    });
  }

  void unblock(@NonNull RecipientId recipientId, @NonNull Runnable success) {
    REDExecutors.BOUNDED.execute(() -> {
      RecipientUtil.unblock(Recipient.resolved(recipientId));
      success.run();
    });
  }
}
