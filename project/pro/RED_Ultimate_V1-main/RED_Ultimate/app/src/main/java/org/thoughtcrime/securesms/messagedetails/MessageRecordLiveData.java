package com.red.sovereign.messagedetails;

import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import org.signal.core.util.concurrent.REDExecutors;
import com.red.sovereign.database.DatabaseObserver;
import com.red.sovereign.database.MessageTable;
import com.red.sovereign.database.NoSuchMessageException;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.database.model.MessageId;
import com.red.sovereign.database.model.MessageRecord;
import com.red.sovereign.dependencies.AppDependencies;

final class MessageRecordLiveData extends LiveData<MessageRecord> {

  private final DatabaseObserver.Observer observer;
  private final MessageId                 messageId;

  MessageRecordLiveData(MessageId messageId) {
    this.messageId = messageId;
    this.observer  = this::retrieveMessageRecordActual;
  }

  @Override
  protected void onActive() {
    REDExecutors.BOUNDED_IO.execute(this::retrieveMessageRecordActual);
  }

  @Override
  protected void onInactive() {
    AppDependencies.getDatabaseObserver().unregisterObserver(observer);
  }

  @WorkerThread
  private synchronized void retrieveMessageRecordActual() {
    try {
      MessageRecord record = MessageTable.withAttachmentData(REDDatabase.messages().getMessageRecord(messageId.getId()));

      if (record.isPaymentNotification()) {
        record = REDDatabase.payments().updateMessageWithPayment(record);
      }

      postValue(record);
      AppDependencies.getDatabaseObserver().registerVerboseConversationObserver(record.getThreadId(), observer);
    } catch (NoSuchMessageException ignored) {
      postValue(null);
    }
  }
}
