package com.red.sovereign.payments;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.signal.core.util.concurrent.REDExecutors;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.dependencies.AppDependencies;

import java.util.UUID;
import java.util.concurrent.Executor;

public class UnreadPaymentsRepository {

  private static final Executor EXECUTOR = REDExecutors.BOUNDED;

  public void markAllPaymentsSeen() {
    EXECUTOR.execute(this::markAllPaymentsSeenInternal);
  }

  public void markPaymentSeen(@NonNull UUID paymentId) {
    EXECUTOR.execute(() -> markPaymentSeenInternal(paymentId));
  }

  @WorkerThread
  private void markAllPaymentsSeenInternal() {
    Context context = AppDependencies.getApplication();
    REDDatabase.payments().markAllSeen();
  }

  @WorkerThread
  private void markPaymentSeenInternal(@NonNull UUID paymentId) {
    Context context = AppDependencies.getApplication();
    REDDatabase.payments().markPaymentSeen(paymentId);
  }

}
