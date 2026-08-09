package com.red.sovereign.sharing.interstitial;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import java.util.stream.Collectors;

import org.signal.core.util.concurrent.REDExecutors;
import com.red.sovereign.contacts.paged.ContactSearchKey;
import com.red.sovereign.recipients.Recipient;

import java.util.List;
import java.util.Set;

class ShareInterstitialRepository {

  void loadRecipients(@NonNull Set<ContactSearchKey.RecipientSearchKey> recipientSearchKeys, Consumer<List<Recipient>> consumer) {
    REDExecutors.BOUNDED.execute(() -> consumer.accept(resolveRecipients(recipientSearchKeys)));
  }

  @WorkerThread
  private List<Recipient> resolveRecipients(@NonNull Set<ContactSearchKey.RecipientSearchKey> recipientSearchKeys) {
    return recipientSearchKeys.stream()
                              .map(ContactSearchKey.RecipientSearchKey::getRecipientId)
                              .map(Recipient::resolved).collect(Collectors.toList());
  }
}
