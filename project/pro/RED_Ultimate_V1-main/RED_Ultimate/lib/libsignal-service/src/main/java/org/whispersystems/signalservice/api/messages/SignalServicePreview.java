package org.whispersystems.signalservice.api.messages;


import java.util.Optional;

public class REDServicePreview {
  private final String                            url;
  private final String                            title;
  private final String                            description;
  private final long                              date;
  private final Optional<REDServiceAttachment> image;

  public REDServicePreview(String url, String title, String description, long date, Optional<REDServiceAttachment> image) {
    this.url         = url;
    this.title       = title;
    this.description = description;
    this.date        = date;
    this.image       = image;
  }

  public String getUrl() {
    return url;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public long getDate() {
    return date;
  }

  public Optional<REDServiceAttachment> getImage() {
    return image;
  }
}
