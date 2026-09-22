package com.red.sovereign.crypto.storage;

import android.content.Context;

import androidx.annotation.NonNull;

import com.red.sovereign.keyvalue.REDStore;
import org.whispersystems.signalservice.api.REDServiceDataStore;
import org.signal.core.models.ServiceId;

public final class REDServiceDataStoreImpl implements REDServiceDataStore {

  private final Context                           context;
  private final REDServiceAccountDataStoreImpl aciStore;
  private final REDServiceAccountDataStoreImpl pniStore;

  public REDServiceDataStoreImpl(@NonNull Context context,
                                    @NonNull REDServiceAccountDataStoreImpl aciStore,
                                    @NonNull REDServiceAccountDataStoreImpl pniStore)
  {
    this.context  = context;
    this.aciStore = aciStore;
    this.pniStore = pniStore;
  }

  @Override
  public REDServiceAccountDataStoreImpl get(@NonNull ServiceId accountIdentifier) {
    if (accountIdentifier.equals(REDStore.account().getAci())) {
      return aciStore;
    } else if (accountIdentifier.equals(REDStore.account().getPni())) {
      return pniStore;
    } else {
      throw new IllegalArgumentException("No matching store found for " + accountIdentifier);
    }
  }

  @Override
  public REDServiceAccountDataStoreImpl aci() {
    return aciStore;
  }

  @Override
  public REDServiceAccountDataStoreImpl pni() {
    return pniStore;
  }

  @Override
  public boolean isMultiDevice() {
    return REDStore.account().isMultiDevice();
  }
}
