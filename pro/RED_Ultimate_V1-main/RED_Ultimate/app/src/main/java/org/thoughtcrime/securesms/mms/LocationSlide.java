package com.red.sovereign.mms;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.red.sovereign.components.location.REDPlace;

import java.util.Optional;


public class LocationSlide extends ImageSlide {

  @NonNull
  private final REDPlace place;

  public LocationSlide(@NonNull  Context context, @NonNull  Uri uri, long size, @NonNull REDPlace place)
  {
    super(context, uri, size, 0, 0, null);
    this.place = place;
  }

  @Override
  @NonNull
  public Optional<String> getBody() {
    return Optional.of(place.getDescription());
  }

  @NonNull
  public REDPlace getPlace() {
    return place;
  }

  @Override
  public boolean hasLocation() {
    return true;
  }

}
