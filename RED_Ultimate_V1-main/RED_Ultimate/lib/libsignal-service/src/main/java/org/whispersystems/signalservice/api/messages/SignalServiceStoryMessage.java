package org.whispersystems.signalservice.api.messages;


import org.whispersystems.signalservice.internal.push.BodyRange;

import java.util.List;
import java.util.Optional;

public class REDServiceStoryMessage {
  private final Optional<byte[]>                      profileKey;
  private final Optional<REDServiceGroupV2>        groupContext;
  private final Optional<REDServiceAttachment>     fileAttachment;
  private final Optional<REDServiceTextAttachment> textAttachment;
  private final Optional<Boolean>                     allowsReplies;
  private final Optional<List<BodyRange>>             bodyRanges;

  private REDServiceStoryMessage(byte[] profileKey,
                                    REDServiceGroupV2 groupContext,
                                    REDServiceAttachment fileAttachment,
                                    REDServiceTextAttachment textAttachment,
                                    boolean allowsReplies,
                                    List<BodyRange> bodyRanges)
  {
    this.profileKey     = Optional.ofNullable(profileKey);
    this.groupContext   = Optional.ofNullable(groupContext);
    this.fileAttachment = Optional.ofNullable(fileAttachment);
    this.textAttachment = Optional.ofNullable(textAttachment);
    this.allowsReplies  = Optional.of(allowsReplies);
    this.bodyRanges     = Optional.ofNullable(bodyRanges);
  }

  public static REDServiceStoryMessage forFileAttachment(byte[] profileKey,
                                                            REDServiceGroupV2 groupContext,
                                                            REDServiceAttachment fileAttachment,
                                                            boolean allowsReplies,
                                                            List<BodyRange> bodyRanges)
  {
    return new REDServiceStoryMessage(profileKey, groupContext, fileAttachment, null, allowsReplies, bodyRanges);
  }

  public static REDServiceStoryMessage forTextAttachment(byte[] profileKey,
                                                            REDServiceGroupV2 groupContext,
                                                            REDServiceTextAttachment textAttachment,
                                                            boolean allowsReplies,
                                                            List<BodyRange> bodyRanges)
  {
    return new REDServiceStoryMessage(profileKey, groupContext, null, textAttachment, allowsReplies, bodyRanges);
  }

  public Optional<byte[]> getProfileKey() {
    return profileKey;
  }

  public Optional<REDServiceGroupV2> getGroupContext() {
    return groupContext;
  }

  public Optional<REDServiceAttachment> getFileAttachment() {
    return fileAttachment;
  }

  public Optional<REDServiceTextAttachment> getTextAttachment() {
    return textAttachment;
  }

  public Optional<Boolean> getAllowsReplies() {
    return allowsReplies;
  }

  public Optional<List<BodyRange>> getBodyRanges() {
    return bodyRanges;
  }
}
