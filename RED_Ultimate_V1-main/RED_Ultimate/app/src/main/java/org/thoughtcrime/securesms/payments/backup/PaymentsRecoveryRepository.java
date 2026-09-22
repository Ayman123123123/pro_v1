package com.red.sovereign.payments.backup;

import androidx.annotation.NonNull;

import com.red.sovereign.keyvalue.REDStore;
import com.red.sovereign.payments.Mnemonic;

public final class PaymentsRecoveryRepository {
  public @NonNull Mnemonic getMnemonic() {
    return REDStore.payments().getPaymentsMnemonic();
  }
}
