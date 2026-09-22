package com.red.sovereign.components.webrtc;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import org.signal.core.util.concurrent.REDExecutors;
import com.red.sovereign.database.GroupTable;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.database.identity.IdentityRecordList;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.recipients.Recipient;

import java.util.Collections;
import java.util.List;

public final class WebRtcCallRepository {

  private WebRtcCallRepository() {}

  @WorkerThread
  public static void getIdentityRecords(@NonNull Recipient recipient, @NonNull Consumer<IdentityRecordList> consumer) {
    REDExecutors.BOUNDED.execute(() -> {
      List<Recipient> recipients;

      if (recipient.isGroup()) {
        recipients = REDDatabase.groups().getGroupMembers(recipient.requireGroupId(), GroupTable.MemberSet.FULL_MEMBERS_EXCLUDING_SELF);
      } else {
        recipients = Collections.singletonList(recipient);
      }

      consumer.accept(AppDependencies.getProtocolStore().aci().identities().getIdentityRecords(recipients));
    });
  }
}
