package org.whispersystems.signalservice.api.messages.multidevice;


import org.whispersystems.signalservice.api.messages.REDServiceAttachment;

public class ContactsMessage {

  private final REDServiceAttachment contacts;
  private final boolean                 complete;

  public ContactsMessage(REDServiceAttachment contacts, boolean complete) {
    this.contacts = contacts;
    this.complete = complete;
  }

  public REDServiceAttachment getContactsStream() {
    return contacts;
  }

  public boolean isComplete() {
    return complete;
  }
}
