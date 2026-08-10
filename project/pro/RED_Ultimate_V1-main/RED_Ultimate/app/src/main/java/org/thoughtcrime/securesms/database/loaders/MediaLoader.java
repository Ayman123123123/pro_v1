package com.red.sovereign.database.loaders;

import android.content.Context;

import com.red.sovereign.util.AbstractCursorLoader;

public abstract class MediaLoader extends AbstractCursorLoader {

  MediaLoader(Context context) {
    super(context);
  }

  public enum MediaType {
    GALLERY,
    DOCUMENT,
    AUDIO,
    LINK,
    ALL
  }
}
