package com.red.sovereign.badges.models

import android.view.View
import android.widget.TextView
import com.bumptech.glide.Glide
import com.red.sovereign.R
import com.red.sovereign.badges.BadgeImageView
import com.red.sovereign.database.model.databaseprotos.GiftBadge
import com.red.sovereign.util.adapter.mapping.LayoutFactory
import com.red.sovereign.util.adapter.mapping.MappingAdapter
import com.red.sovereign.util.adapter.mapping.MappingModel
import com.red.sovereign.util.adapter.mapping.MappingViewHolder
import com.red.sovereign.util.visible

/**
 * Displays a 112dp badge.
 */
object BadgeDisplay112 {
  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(Model::class.java, LayoutFactory(::ViewHolder, R.layout.badge_display_112))
    mappingAdapter.registerFactory(GiftModel::class.java, LayoutFactory(::GiftViewHolder, R.layout.badge_display_112))
  }

  class Model(val badge: Badge, val withDisplayText: Boolean = true) : MappingModel<Model> {
    override fun areItemsTheSame(newItem: Model): Boolean = badge.id == newItem.badge.id

    override fun areContentsTheSame(newItem: Model): Boolean = badge == newItem.badge && withDisplayText == newItem.withDisplayText
  }

  class GiftModel(val giftBadge: GiftBadge) : MappingModel<GiftModel> {
    override fun areItemsTheSame(newItem: GiftModel): Boolean = giftBadge.redemptionToken == newItem.giftBadge.redemptionToken
    override fun areContentsTheSame(newItem: GiftModel): Boolean = giftBadge == newItem.giftBadge
  }

  class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {
    private val badgeImageView: BadgeImageView = itemView.findViewById(R.id.badge)
    private val titleView: TextView = itemView.findViewById(R.id.name)

    override fun bind(model: Model) {
      titleView.text = model.badge.name
      titleView.visible = model.withDisplayText
      badgeImageView.setBadge(model.badge)
    }
  }

  class GiftViewHolder(itemView: View) : MappingViewHolder<GiftModel>(itemView) {
    private val badgeImageView: BadgeImageView = itemView.findViewById(R.id.badge)
    private val titleView: TextView = itemView.findViewById(R.id.name)

    override fun bind(model: GiftModel) {
      titleView.visible = false
      badgeImageView.setGiftBadge(model.giftBadge, Glide.with(badgeImageView))
    }
  }
}
