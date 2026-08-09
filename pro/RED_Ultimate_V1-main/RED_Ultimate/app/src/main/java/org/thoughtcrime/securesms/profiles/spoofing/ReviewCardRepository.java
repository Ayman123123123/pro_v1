package com.red.sovereign.profiles.spoofing;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.signal.core.util.concurrent.REDExecutors;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.database.ThreadTable;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.groups.GroupChangeException;
import com.red.sovereign.groups.GroupId;
import com.red.sovereign.groups.GroupManager;
import com.red.sovereign.groups.GroupsInCommonRepository;
import com.red.sovereign.jobs.MultiDeviceMessageRequestResponseJob;
import com.red.sovereign.keyvalue.REDStore;
import com.red.sovereign.recipients.Recipient;
import com.red.sovereign.recipients.RecipientId;
import com.red.sovereign.recipients.RecipientUtil;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

class ReviewCardRepository {

  private final Context     context;
  private final GroupId.V2  groupId;
  private final RecipientId recipientId;

  protected ReviewCardRepository(@NonNull Context context,
                                 @NonNull GroupId.V2 groupId)
  {
    this.context     = context;
    this.groupId     = groupId;
    this.recipientId = null;
  }

  protected ReviewCardRepository(@NonNull Context context,
                                 @NonNull RecipientId recipientId)
  {
    this.context     = context;
    this.groupId     = null;
    this.recipientId = recipientId;
  }

  void loadRecipients(@NonNull OnRecipientsLoadedListener onRecipientsLoadedListener) {
    if (groupId != null) {
      loadRecipientsForGroup(groupId, onRecipientsLoadedListener);
    } else if (recipientId != null) {
      loadSimilarRecipients(recipientId, onRecipientsLoadedListener);
    } else {
      throw new AssertionError();
    }
  }

  @WorkerThread
  int loadGroupsInCommonCount(@NonNull ReviewRecipient reviewRecipient) {
    return GroupsInCommonRepository.getGroupsInCommonCountSync(reviewRecipient.getRecipient().getId());
  }

  void block(@NonNull ReviewCard reviewCard, @NonNull Runnable onActionCompleteListener) {
    REDExecutors.BOUNDED.execute(() -> {
      RecipientUtil.blockNonGroup(context, reviewCard.getReviewRecipient());
      onActionCompleteListener.run();
    });
  }

  void delete(@NonNull ReviewCard reviewCard, @NonNull Runnable onActionCompleteListener) {
    if (recipientId == null) {
      throw new UnsupportedOperationException();
    }

    REDExecutors.BOUNDED.execute(() -> {
      Recipient resolved = Recipient.resolved(recipientId);

      if (resolved.isGroup()) throw new AssertionError();

      if (REDStore.account().isMultiDevice()) {
        AppDependencies.getJobManager().add(MultiDeviceMessageRequestResponseJob.forDelete(recipientId));
      }

      ThreadTable threadTable = REDDatabase.threads();
      long        threadId    = Objects.requireNonNull(threadTable.getThreadIdFor(recipientId));

      threadTable.deleteConversation(threadId, false);
      onActionCompleteListener.run();
    });
  }

  void removeFromGroup(@NonNull ReviewCard reviewCard, @NonNull OnRemoveFromGroupListener onRemoveFromGroupListener) {
    if (groupId == null) {
      throw new UnsupportedOperationException();
    }

    REDExecutors.BOUNDED.execute(() -> {
      try {
        GroupManager.ejectAndBanFromGroup(context, groupId, reviewCard.getReviewRecipient());
        onRemoveFromGroupListener.onActionCompleted();
      } catch (GroupChangeException | IOException e) {
        onRemoveFromGroupListener.onActionFailed();
      }
    });
  }

  private static void loadRecipientsForGroup(@NonNull GroupId.V2 groupId,
                                             @NonNull OnRecipientsLoadedListener onRecipientsLoadedListener)
  {
    REDExecutors.BOUNDED.execute(() -> {
      RecipientId groupRecipientId = REDDatabase.recipients().getByGroupId(groupId).orElse(null);
      if (groupRecipientId != null) {
        onRecipientsLoadedListener.onRecipientsLoaded(REDDatabase.nameCollisions().getCollisionsForThreadRecipientId(groupRecipientId));
      } else {
        onRecipientsLoadedListener.onRecipientsLoadFailed();
      }
    });
  }

  private static void loadSimilarRecipients(@NonNull RecipientId recipientId,
                                            @NonNull OnRecipientsLoadedListener onRecipientsLoadedListener)
  {
    REDExecutors.BOUNDED.execute(() -> {
      onRecipientsLoadedListener.onRecipientsLoaded(REDDatabase.nameCollisions().getCollisionsForThreadRecipientId(recipientId));
    });
  }

  interface OnRecipientsLoadedListener {
    void onRecipientsLoaded(@NonNull List<ReviewRecipient> recipients);
    void onRecipientsLoadFailed();
  }

  interface OnRemoveFromGroupListener {
    void onActionCompleted();
    void onActionFailed();
  }
}
