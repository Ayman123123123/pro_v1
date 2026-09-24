package com.red.sovereign.payments.currency;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import org.signal.core.util.concurrent.REDExecutors;
import org.signal.core.util.logging.Log;
import com.red.sovereign.payments.Payments;
import com.red.sovereign.util.AsynchronousCallback;

import java.io.IOException;

public final class CurrencyExchangeRepository {

  private static final String TAG = Log.tag(CurrencyExchangeRepository.class);

  private final Payments payments;

  public CurrencyExchangeRepository(@NonNull Payments payments) {
    this.payments = payments;
  }

  @AnyThread
  public void getCurrencyExchange(@NonNull AsynchronousCallback.WorkerThread<CurrencyExchange, Throwable> callback, boolean refreshIfAble) {
    REDExecutors.BOUNDED.execute(() -> {
      try {
        callback.onComplete(payments.getCurrencyExchange(refreshIfAble));
      } catch (IOException e) {
        Log.w(TAG, e);
        callback.onError(e);
      }
    });
  }
}
