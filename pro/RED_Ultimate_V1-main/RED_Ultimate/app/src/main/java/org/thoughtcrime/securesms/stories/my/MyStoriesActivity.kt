package com.red.sovereign.stories.my

import androidx.fragment.app.Fragment
import com.red.sovereign.components.FragmentWrapperActivity

class MyStoriesActivity : FragmentWrapperActivity() {
  override fun getFragment(): Fragment {
    return MyStoriesFragment()
  }
}
