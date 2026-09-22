package com.red.sovereign.payments;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import org.signal.core.util.concurrent.REDExecutors;
import com.red.sovereign.database.DatabaseObserver;
import com.red.sovereign.database.PaymentTable;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.util.concurrent.SerialMonoLifoExecutor;

import java.util.UUID;
import java.util.concurrent.Executor;

public final class PaymentTransactionLiveData extends LiveData<PaymentTable.PaymentTransaction> {

  private final UUID                      paymentId;
  private final PaymentTable              paymentDatabase;
  private final DatabaseObserver.Observer observer;
  private final Executor                  executor;

  public PaymentTransactionLiveData(@NonNull UUID paymentId) {
    this.paymentId       = paymentId;
    this.paymentDatabase = REDDatabase.payments();
    this.observer        = this::getPaymentTransaction;
    this.executor        = new SerialMonoLifoExecutor(REDExecutors.BOUNDED);
  }

  @Override
  protected void onActive() {
    getPaymentTransaction();
    AppDependencies.getDatabaseObserver().registerPaymentObserver(paymentId, observer);
  }

  @Override
  protected void onInactive() {
    AppDependencies.getDatabaseObserver().unregisterObserver(observer);
  }

  private void getPaymentTransaction() {
    executor.execute(() -> postValue(paymentDatabase.getPayment(paymentId)));
  }
}
