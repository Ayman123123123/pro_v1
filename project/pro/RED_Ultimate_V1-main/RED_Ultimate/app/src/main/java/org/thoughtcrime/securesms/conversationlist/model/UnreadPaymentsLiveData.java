package com.red.sovereign.conversationlist.model;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import org.signal.core.util.concurrent.REDExecutors;
import com.red.sovereign.database.DatabaseObserver;
import com.red.sovereign.database.PaymentTable;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.recipients.Recipient;
import com.red.sovereign.util.concurrent.SerialMonoLifoExecutor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * LiveData encapsulating the logic to watch the Payments database for changes to payments and supply
 * a list of unread payments to listeners. If there are no unread payments, Optional.empty() will be passed
 * through instead.
 */
public final class UnreadPaymentsLiveData extends LiveData<Optional<UnreadPayments>> {

  private final PaymentTable              paymentDatabase;
  private final DatabaseObserver.Observer observer;
  private final Executor                  executor;

  public UnreadPaymentsLiveData() {
    this.paymentDatabase = REDDatabase.payments();
    this.observer        = this::refreshUnreadPayments;
    this.executor        = new SerialMonoLifoExecutor(REDExecutors.BOUNDED);
  }

  @Override
  protected void onActive() {
    refreshUnreadPayments();
    AppDependencies.getDatabaseObserver().registerAllPaymentsObserver(observer);
  }

  @Override
  protected void onInactive() {
    AppDependencies.getDatabaseObserver().unregisterObserver(observer);
  }

  private void refreshUnreadPayments() {
    executor.execute(() -> postValue(Optional.ofNullable(getUnreadPayments())));
  }

  @WorkerThread
  private @Nullable UnreadPayments getUnreadPayments() {
    List<PaymentTable.PaymentTransaction> unseenPayments = paymentDatabase.getUnseenPayments();
    int                                   unseenCount    = unseenPayments.size();

    switch (unseenCount) {
      case 0:
        return null;
      case 1:
        PaymentTable.PaymentTransaction transaction = unseenPayments.get(0);
        Recipient                          recipient   = transaction.getPayee().hasRecipientId()
                                                         ? Recipient.resolved(transaction.getPayee().requireRecipientId())
                                                         : null;

        return UnreadPayments.forSingle(recipient, transaction.getUuid(), transaction.getAmount());
      default:
        return UnreadPayments.forMultiple(unseenCount);
    }
  }
}
