package org.whispersystems.signalservice.api.messages;

import org.whispersystems.signalservice.api.push.REDServiceAddress;

import java.util.List;

public class REDServiceStoryMessageRecipient {

  private final REDServiceAddress signalServiceAddress;
  private final List<String>         distributionListIds;
  private final boolean              isAllowedToReply;

  public REDServiceStoryMessageRecipient(REDServiceAddress signalServiceAddress,
                                            List<String> distributionListIds,
                                            boolean isAllowedToReply)
  {
    this.signalServiceAddress = signalServiceAddress;
    this.distributionListIds  = distributionListIds;
    this.isAllowedToReply     = isAllowedToReply;
  }

  public List<String> getDistributionListIds() {
    return distributionListIds;
  }

  public REDServiceAddress getREDServiceAddress() {
    return signalServiceAddress;
  }

  public boolean isAllowedToReply() {
    return isAllowedToReply;
  }
}
