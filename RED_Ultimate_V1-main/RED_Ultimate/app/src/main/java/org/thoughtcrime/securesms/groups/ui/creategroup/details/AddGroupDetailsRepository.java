package com.red.sovereign.groups.ui.creategroup.details;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import org.signal.core.util.concurrent.REDExecutors;
import org.signal.core.util.logging.Log;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.database.model.GroupRecord;
import com.red.sovereign.groups.GroupChangeBusyException;
import com.red.sovereign.groups.GroupChangeException;
import com.red.sovereign.groups.GroupManager;
import com.red.sovereign.groups.ui.GroupMemberEntry;
import com.red.sovereign.keyvalue.REDStore;
import com.red.sovereign.recipients.Recipient;
import com.red.sovereign.recipients.RecipientId;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

final class AddGroupDetailsRepository {

  private static String TAG = Log.tag(AddGroupDetailsRepository.class);

  private final Context context;

  AddGroupDetailsRepository(@NonNull Context context) {
    this.context = context;
  }

  void resolveMembers(@NonNull Collection<RecipientId> recipientIds, Consumer<List<GroupMemberEntry.NewGroupCandidate>> consumer) {
    REDExecutors.BOUNDED.execute(() -> {
      List<GroupMemberEntry.NewGroupCandidate> members = new ArrayList<>(recipientIds.size());

      for (RecipientId id : recipientIds) {
        members.add(new GroupMemberEntry.NewGroupCandidate(Recipient.resolved(id)));
      }

      consumer.accept(members);
    });
  }

  void getGroupsWithSameMembers(@NonNull Set<RecipientId> memberIds, Consumer<List<Recipient>> consumer) {
    REDExecutors.BOUNDED.execute(() -> {
      List<GroupRecord> groups     = REDDatabase.groups().getGroupsWithExactMembers(memberIds);
      List<Recipient>   recipients = new ArrayList<>(groups.size());

      for (GroupRecord group : groups) {
        recipients.add(Recipient.resolved(group.getRecipientId()));
      }

      consumer.accept(recipients);
    });
  }

  void createGroup(@NonNull Set<RecipientId> members,
                   @Nullable byte[] avatar,
                   @Nullable String name,
                   @Nullable Integer disappearingMessagesTimer,
                   Consumer<GroupCreateResult> resultConsumer)
  {
    REDExecutors.BOUNDED.execute(() -> {
      try {
        GroupManager.GroupActionResult result = GroupManager.createGroup(context,
                                                                         members,
                                                                         avatar,
                                                                         name,
                                                                         disappearingMessagesTimer != null ? disappearingMessagesTimer
                                                                                                           : REDStore.settings().getUniversalExpireTimer());

        resultConsumer.accept(GroupCreateResult.success(result));
      } catch (GroupChangeBusyException e) {
        Log.w(TAG, "Unable to create group, group busy", e);
        resultConsumer.accept(GroupCreateResult.error(GroupCreateResult.Error.Type.ERROR_BUSY));
      } catch (GroupChangeException e) {
        Log.w(TAG, "Unable to create group, group change failed", e);
        resultConsumer.accept(GroupCreateResult.error(GroupCreateResult.Error.Type.ERROR_FAILED));
      } catch (IOException e) {
        Log.w(TAG, "Unable to create group, unknown IO", e);
        resultConsumer.accept(GroupCreateResult.error(GroupCreateResult.Error.Type.ERROR_IO));
      }
    });
  }
}
