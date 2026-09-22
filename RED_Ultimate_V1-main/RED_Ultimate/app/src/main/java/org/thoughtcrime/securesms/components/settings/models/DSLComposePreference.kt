/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.models

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import org.signal.core.ui.compose.theme.REDTheme
import com.red.sovereign.util.adapter.mapping.Factory
import com.red.sovereign.util.adapter.mapping.MappingAdapter
import com.red.sovereign.util.adapter.mapping.MappingModel
import com.red.sovereign.util.adapter.mapping.MappingViewHolder

/**
 * Allows hosting compose code in a DSL adapter.
 */
object DSLComposePreference {
  /**
   * Initializes the ComposeView to play nice with RecyclerView and manages the Model in a State.
   */
  abstract class ViewHolder<T : MappingModel<T>>(composeView: ComposeView) : MappingViewHolder<T>(composeView) {

    private var model: T? by mutableStateOf(null)

    init {
      composeView.setViewCompositionStrategy(
        ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
      )

      composeView.setContent {
        val model = this.model ?: return@setContent

        REDTheme {
          Content(model)
        }
      }
    }

    override fun bind(model: T) {
      this.model = model
    }

    @Composable
    abstract fun Content(model: T)
  }

  /**
   * Does not need to be used directly, but does need to be non-private so that the inline register method can see it.
   */
  class ComposeFactory<T : MappingModel<T>>(
    private val create: (ComposeView) -> MappingViewHolder<T>
  ) : Factory<T> {
    override fun createViewHolder(parent: ViewGroup): MappingViewHolder<T> {
      return create(ComposeView(parent.context))
    }
  }

  inline fun <reified T : MappingModel<T>> register(adapter: MappingAdapter, noinline create: (ComposeView) -> MappingViewHolder<T>) {
    adapter.registerFactory(T::class.java, ComposeFactory(create))
  }
}
