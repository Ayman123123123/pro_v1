package com.red.sovereign.conversation.colors.ui.custom

import com.red.sovereign.wallpaper.ChatWallpaper
import java.util.EnumMap

data class CustomChatColorCreatorState(
  val loading: Boolean,
  val wallpaper: ChatWallpaper?,
  val sliderStates: EnumMap<CustomChatColorEdge, ColorSlidersState>,
  val selectedEdge: CustomChatColorEdge,
  val degrees: Float
)

data class ColorSlidersState(val huePosition: Int, val saturationPosition: Int)
