package com.red.sovereign.payments.backup.phrase;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import org.signal.core.util.concurrent.REDExecutors;
import org.signal.core.util.logging.Log;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.jobs.PaymentLedgerUpdateJob;
import com.red.sovereign.jobs.ProfileUploadJob;
import com.red.sovereign.keyvalue.PaymentsValues;
import com.red.sovereign.keyvalue.REDStore;
import org.signal.core.util.Util;

import java.util.List;

class PaymentsRecoveryPhraseRepository {

  private static final String TAG = Log.tag(PaymentsRecoveryPhraseRepository.class);

  void restoreMnemonic(@NonNull List<String> words,
                       @NonNull Consumer<PaymentsValues.WalletRestoreResult> resultConsumer)
  {
    REDExecutors.BOUNDED.execute(() -> {
      String                             mnemonic = Util.join(words, " ");
      PaymentsValues.WalletRestoreResult result   = REDStore.payments().restoreWallet(mnemonic);

      switch (result) {
        case ENTROPY_CHANGED:
          Log.i(TAG, "restoreMnemonic: mnemonic resulted in entropy mismatch, flushing cached values");
          REDDatabase.payments().deleteAll();
          AppDependencies.getPayments().closeWallet();
          updateProfileAndFetchLedger();
          break;
        case ENTROPY_UNCHANGED:
          Log.i(TAG, "restoreMnemonic: mnemonic resulted in entropy match, no flush needed.");
          updateProfileAndFetchLedger();
          break;
        case MNEMONIC_ERROR:
          Log.w(TAG, "restoreMnemonic: failed to restore wallet from given mnemonic.");
          break;
      }

      resultConsumer.accept(result);
    });
  }

  private void updateProfileAndFetchLedger() {
    AppDependencies.getJobManager()
                   .startChain(new ProfileUploadJob())
                   .then(PaymentLedgerUpdateJob.updateLedger())
                   .enqueue();
  }
}
