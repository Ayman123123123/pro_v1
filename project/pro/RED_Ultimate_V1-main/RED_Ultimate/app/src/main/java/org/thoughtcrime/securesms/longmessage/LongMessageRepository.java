package com.red.sovereign.longmessage;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.signal.core.util.concurrent.REDExecutors;
import org.signal.core.util.logging.Log;
import com.red.sovereign.conversation.ConversationMessage;
import com.red.sovereign.database.MessageTable;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.database.model.MessageRecord;
import com.red.sovereign.database.model.MmsMessageRecord;

import java.util.Optional;

class LongMessageRepository {

  private final static String TAG = Log.tag(LongMessageRepository.class);

  private final MessageTable messageTable;

  LongMessageRepository() {
    this.messageTable = REDDatabase.messages();
  }

  void getMessage(@NonNull Context context, long messageId, @NonNull Callback<Optional<LongMessage>> callback) {
    REDExecutors.BOUNDED.execute(() -> {
      callback.onComplete(getMmsLongMessage(context, messageTable, messageId));
    });
  }

  @WorkerThread
  private Optional<LongMessage> getMmsLongMessage(@NonNull Context context, @NonNull MessageTable mmsDatabase, long messageId) {
    Optional<MmsMessageRecord> record = getMmsMessage(mmsDatabase, messageId);
    if (record.isPresent()) {
      final ConversationMessage resolvedMessage = LongMessageResolveerKt.resolveBody(record.get(), context);
      return  Optional.of(new LongMessage(resolvedMessage, context));
    } else {
      return Optional.empty();
    }
  }

  @WorkerThread
  private Optional<MmsMessageRecord> getMmsMessage(@NonNull MessageTable mmsDatabase, long messageId) {
    try (Cursor cursor = mmsDatabase.getMessageCursor(messageId)) {
      MessageRecord record = MessageTable.mmsReaderFor(cursor).getNext();
      if (record != null) {
        record = MessageTable.withAttachmentData(record);
      }
      return Optional.ofNullable((MmsMessageRecord) record);
    }
  }


  interface Callback<T> {
    void onComplete(T result);
  }
}
