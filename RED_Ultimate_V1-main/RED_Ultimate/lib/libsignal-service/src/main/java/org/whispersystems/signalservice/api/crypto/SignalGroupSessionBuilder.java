package org.whispersystems.signalservice.api.crypto;

import org.signal.libsignal.protocol.SessionBuilder;
import org.signal.libsignal.protocol.REDProtocolAddress;
import org.signal.libsignal.protocol.groups.GroupSessionBuilder;
import org.signal.libsignal.protocol.message.SenderKeyDistributionMessage;
import org.whispersystems.signalservice.api.REDSessionLock;

import java.util.UUID;

/**
 * A thread-safe wrapper around {@link SessionBuilder}.
 */
public class REDGroupSessionBuilder {

  private final REDSessionLock   lock;
  private final GroupSessionBuilder builder;

  public REDGroupSessionBuilder(REDSessionLock lock, GroupSessionBuilder builder) {
    this.lock    = lock;
    this.builder = builder;
  }

  public void process(REDProtocolAddress sender, SenderKeyDistributionMessage senderKeyDistributionMessage) {
    try (REDSessionLock.Lock unused = lock.acquire()) {
      builder.process(sender, senderKeyDistributionMessage);
    }
  }

  public SenderKeyDistributionMessage create(REDProtocolAddress sender, UUID distributionId) {
    try (REDSessionLock.Lock unused = lock.acquire()) {
      return builder.create(sender, distributionId);
    }
  }
}
