package com.red.sovereign.components.settings.app.notifications.profiles.models

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import com.red.sovereign.R
import com.red.sovereign.components.emoji.EmojiUtil
import com.red.sovereign.util.adapter.mapping.LayoutFactory
import com.red.sovereign.util.adapter.mapping.MappingAdapter
import com.red.sovereign.util.adapter.mapping.MappingModel
import com.red.sovereign.util.adapter.mapping.MappingViewHolder

/**
 * DSL custom preference for showing default emoji/name combos for create/edit profile.
 */
object NotificationProfileNamePreset {
  fun register(adapter: MappingAdapter) {
    adapter.registerFactory(Model::class.java, LayoutFactory({ ViewHolder(it) }, R.layout.about_preset_item))
  }

  class Model(val emoji: String, @StringRes val bodyResource: Int, val onClick: (Model) -> Unit) : MappingModel<Model> {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return bodyResource == newItem.bodyResource
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return areItemsTheSame(newItem) && emoji == newItem.emoji
    }
  }

  class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    val emoji: ImageView = findViewById(R.id.about_preset_emoji)
    val body: TextView = findViewById(R.id.about_preset_body)

    override fun bind(model: Model) {
      itemView.setOnClickListener { model.onClick(model) }
      emoji.setImageDrawable(EmojiUtil.convertToDrawable(context, model.emoji))
      body.setText(model.bodyResource)
    }
  }
}
