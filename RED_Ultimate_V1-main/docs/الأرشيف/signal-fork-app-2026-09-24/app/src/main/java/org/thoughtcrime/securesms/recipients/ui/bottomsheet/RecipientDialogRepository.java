package com.red.sovereign.recipients.ui.bottomsheet;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import org.signal.core.models.ServiceId;
import org.signal.core.util.concurrent.REDExecutors;
import org.signal.core.util.concurrent.SimpleTask;
import org.signal.core.util.logging.Log;
import com.red.sovereign.contacts.sync.ContactDiscovery;
import com.red.sovereign.database.GroupTable;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.database.model.GroupRecord;
import com.red.sovereign.database.model.IdentityRecord;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.groups.GroupChangeException;
import com.red.sovereign.groups.GroupId;
import com.red.sovereign.groups.GroupManager;
import com.red.sovereign.groups.ui.GroupChangeErrorCallback;
import com.red.sovereign.groups.ui.GroupChangeFailureReason;
import com.red.sovereign.recipients.Recipient;
import com.red.sovereign.recipients.RecipientId;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class RecipientDialogRepository {

  private static final String TAG = Log.tag(RecipientDialogRepository.class);

  @NonNull  private final Context     context;
  @NonNull  private final RecipientId recipientId;
  @Nullable private final GroupId     groupId;

  RecipientDialogRepository(@NonNull Context context,
                            @NonNull RecipientId recipientId,
                            @Nullable GroupId groupId)
  {
    this.context     = context;
    this.recipientId = recipientId;
    this.groupId     = groupId;
  }

  @NonNull RecipientId getRecipientId() {
    return recipientId;
  }

  @Nullable GroupId getGroupId() {
    return groupId;
  }

  void getIdentity(@NonNull Consumer<IdentityRecord> callback) {
    REDExecutors.BOUNDED.execute(
      () -> callback.accept(AppDependencies.getProtocolStore().aci().identities().getIdentityRecord(recipientId).orElse(null)));
  }

  void getRecipient(@NonNull RecipientCallback recipientCallback) {
    SimpleTask.run(REDExecutors.BOUNDED,
                   () -> Recipient.resolved(recipientId),
                   recipientCallback::onRecipient);
  }

  void refreshRecipient() {
    REDExecutors.UNBOUNDED.execute(() -> {
      try {
        ContactDiscovery.refresh(context, Recipient.resolved(recipientId), false);
      } catch (IOException e) {
        Log.w(TAG, "Failed to refresh user after adding to contacts.");
      }
    });
  }

  void removeMember(@NonNull Consumer<Boolean> onComplete, @NonNull GroupChangeErrorCallback error) {
    SimpleTask.run(REDExecutors.UNBOUNDED,
                   () -> {
                     try {
                       GroupManager.ejectAndBanFromGroup(context, Objects.requireNonNull(groupId).requireV2(), Recipient.resolved(recipientId));
                       return true;
                     } catch (GroupChangeException | IOException e) {
                       Log.w(TAG, e);
                       error.onError(GroupChangeFailureReason.fromException(e));
                     }
                     return false;
                   },
                   onComplete::accept);
  }

  void setMemberAdmin(boolean admin, @NonNull Consumer<Boolean> onComplete, @NonNull GroupChangeErrorCallback error) {
    SimpleTask.run(REDExecutors.UNBOUNDED,
                   () -> {
                     try {
                       GroupManager.setMemberAdmin(context, Objects.requireNonNull(groupId).requireV2(), recipientId, admin);
                       return true;
                     } catch (GroupChangeException | IOException e) {
                       Log.w(TAG, e);
                       error.onError(GroupChangeFailureReason.fromException(e));
                     }
                     return false;
                   },
                   onComplete::accept);
  }

  void willAdminDemotionClearLabel(@NonNull Consumer<Boolean> onComplete) {
    SimpleTask.BackgroundTask<Boolean> hasLabelToClear = () -> {
      if (groupId == null || !groupId.isV2()) {
        return false;
      }

      GroupRecord   groupRecord = REDDatabase.groups().getGroup(groupId.requireV2()).orElse(null);
      ServiceId.ACI aci         = Recipient.resolved(recipientId).getAci().orElse(null);

      if (groupRecord != null && groupRecord.getHasV2GroupProperties() && aci != null) {
        return groupRecord.requireV2GroupProperties().adminDemotionClearsLabel(aci);
      }
      return false;
    };

    SimpleTask.run(REDExecutors.UNBOUNDED, hasLabelToClear, onComplete::accept);
  }

  void getGroupMembership(@NonNull Consumer<List<RecipientId>> onComplete) {
    SimpleTask.run(REDExecutors.UNBOUNDED,
                   () -> {
                     GroupTable             groupDatabase   = REDDatabase.groups();
                     List<GroupRecord>      groupRecords    = groupDatabase.getPushGroupsContainingMember(recipientId);
                     ArrayList<RecipientId> groupRecipients = new ArrayList<>(groupRecords.size());

                     for (GroupRecord groupRecord : groupRecords) {
                       groupRecipients.add(groupRecord.getRecipientId());
                     }

                     return groupRecipients;
                   },
                   onComplete::accept);
  }

  public void getActiveGroupCount(@NonNull Consumer<Integer> onComplete) {
    REDExecutors.BOUNDED.execute(() -> onComplete.accept(REDDatabase.groups().getActiveGroupCount()));
  }

  interface RecipientCallback {
    void onRecipient(@NonNull Recipient recipient);
  }
}
