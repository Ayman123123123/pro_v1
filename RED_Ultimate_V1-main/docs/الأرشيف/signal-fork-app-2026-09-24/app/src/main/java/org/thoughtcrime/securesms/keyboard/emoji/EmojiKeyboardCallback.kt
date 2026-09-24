package com.red.sovereign.keyboard.emoji

import com.red.sovereign.components.emoji.EmojiEventListener
import com.red.sovereign.keyboard.emoji.search.EmojiSearchFragment

interface EmojiKeyboardCallback :
  EmojiEventListener,
  EmojiKeyboardPageFragment.Callback,
  EmojiSearchFragment.Callback
