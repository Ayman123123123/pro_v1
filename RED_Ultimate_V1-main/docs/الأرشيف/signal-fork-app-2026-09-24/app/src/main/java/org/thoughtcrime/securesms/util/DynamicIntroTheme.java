package com.red.sovereign.util;

import androidx.annotation.StyleRes;

import com.red.sovereign.R;

public class DynamicIntroTheme extends DynamicTheme {

  protected @StyleRes int getTheme() {
    return R.style.RED_DayNight_IntroTheme;
  }
}
