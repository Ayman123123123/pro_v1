package com.red.sovereign.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.database.ThreadTable;
import com.red.sovereign.database.model.ThreadWithRecipient;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.jobmanager.Job;
import com.red.sovereign.keyvalue.REDStore;
import com.red.sovereign.recipients.Recipient;
import com.red.sovereign.transport.RetryLaterException;
import com.red.sovereign.util.ConversationUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * On some devices, interacting with the ShortcutManager can take a very long time (several seconds).
 * So, we interact with it in a job instead, and keep it in one queue so it can't starve the other
 * job runners.
 */
public class ConversationShortcutUpdateJob extends BaseJob {

  private static final String TAG = Log.tag(ConversationShortcutUpdateJob.class);

  public static final String KEY = "ConversationShortcutUpdateJob";

  public static void enqueue() {
    AppDependencies.getJobManager().add(new ConversationShortcutUpdateJob());
  }

  private ConversationShortcutUpdateJob() {
    this(new Parameters.Builder()
                       .setQueue("ConversationShortcutUpdateJob")
                       .setLifespan(TimeUnit.MINUTES.toMillis(15))
                       .setMaxInstancesForFactory(1)
                       .setGlobalPriority(Parameters.PRIORITY_LOW)
                       .build());
  }

  private ConversationShortcutUpdateJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public @Nullable byte[] serialize() {
    return null;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws Exception {
    if (REDStore.settings().getScreenLockEnabled()) {
      Log.i(TAG, "Screen lock enabled. Clearing shortcuts.");
      ConversationUtil.clearAllShortcuts(context);
      return;
    }

    if (REDStore.account().getAci() == null) {
      Log.i(TAG, "Need ACI for group shortcuts");
      return;
    }

    ThreadTable threadTable  = REDDatabase.threads();
    int         maxShortcuts = ConversationUtil.getMaxShortcuts(context);
    List<Recipient> ranked         = new ArrayList<>(maxShortcuts);

    try (ThreadTable.Reader reader = threadTable.readerFor(threadTable.getRecentConversationList(maxShortcuts, false, false, false, true, true, false))) {
      ThreadWithRecipient record;
      while ((record = reader.getNext()) != null) {
        ranked.add(record.getRecipient().resolve());
      }
    }

    boolean success = ConversationUtil.setActiveShortcuts(context, ranked);

    if (!success) {
      throw new RetryLaterException();
    }

    ConversationUtil.removeLongLivedShortcuts(context, threadTable.getArchivedRecipients());
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof RetryLaterException;
  }

  @Override
  public void onFailure() {
  }

  public static class Factory implements Job.Factory<ConversationShortcutUpdateJob> {
    @Override
    public @NonNull ConversationShortcutUpdateJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new ConversationShortcutUpdateJob(parameters);
    }
  }
}
