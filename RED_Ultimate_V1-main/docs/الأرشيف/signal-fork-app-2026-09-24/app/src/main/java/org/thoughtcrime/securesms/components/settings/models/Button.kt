package com.red.sovereign.components.settings.models

import android.view.View
import com.google.android.material.button.MaterialButton
import com.red.sovereign.R
import com.red.sovereign.components.settings.DSLSettingsIcon
import com.red.sovereign.components.settings.DSLSettingsText
import com.red.sovereign.components.settings.PreferenceModel
import com.red.sovereign.util.adapter.mapping.LayoutFactory
import com.red.sovereign.util.adapter.mapping.MappingAdapter
import com.red.sovereign.util.adapter.mapping.MappingViewHolder

object Button {

  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(Model.Primary::class.java, LayoutFactory({ ViewHolder(it) }, R.layout.dsl_button_primary))
    mappingAdapter.registerFactory(Model.PrimaryWrapped::class.java, LayoutFactory({ ViewHolder(it) }, R.layout.dsl_button_primary_wrapped))
    mappingAdapter.registerFactory(Model.Tonal::class.java, LayoutFactory({ ViewHolder(it) }, R.layout.dsl_button_tonal))
    mappingAdapter.registerFactory(Model.TonalWrapped::class.java, LayoutFactory({ ViewHolder(it) }, R.layout.dsl_button_tonal_wrapped))
    mappingAdapter.registerFactory(Model.SecondaryNoOutline::class.java, LayoutFactory({ ViewHolder(it) }, R.layout.dsl_button_secondary))
  }

  sealed class Model<T : Model<T>>(
    title: DSLSettingsText?,
    icon: DSLSettingsIcon?,
    isEnabled: Boolean,
    val disableOnClick: Boolean,
    val onClick: () -> Unit
  ) : PreferenceModel<T>(
    title = title,
    icon = icon,
    isEnabled = isEnabled
  ) {
    /**
     * Large primary button with width set to match_parent
     */
    class Primary(
      title: DSLSettingsText?,
      icon: DSLSettingsIcon?,
      isEnabled: Boolean,
      disableOnClick: Boolean,
      onClick: () -> Unit
    ) : Model<Primary>(title, icon, isEnabled, disableOnClick, onClick)

    /**
     * Large primary button with width set to wrap_content
     */
    class PrimaryWrapped(
      title: DSLSettingsText?,
      icon: DSLSettingsIcon?,
      isEnabled: Boolean,
      disableOnClick: Boolean,
      onClick: () -> Unit
    ) : Model<PrimaryWrapped>(title, icon, isEnabled, disableOnClick, onClick)

    class Tonal(
      title: DSLSettingsText?,
      icon: DSLSettingsIcon?,
      isEnabled: Boolean,
      disableOnClick: Boolean,
      onClick: () -> Unit
    ) : Model<Tonal>(title, icon, isEnabled, disableOnClick, onClick)

    class TonalWrapped(
      title: DSLSettingsText?,
      icon: DSLSettingsIcon?,
      isEnabled: Boolean,
      disableOnClick: Boolean,
      onClick: () -> Unit
    ) : Model<TonalWrapped>(title, icon, isEnabled, disableOnClick, onClick)

    class SecondaryNoOutline(
      title: DSLSettingsText?,
      icon: DSLSettingsIcon?,
      isEnabled: Boolean,
      disableOnClick: Boolean,
      onClick: () -> Unit
    ) : Model<SecondaryNoOutline>(title, icon, isEnabled, disableOnClick, onClick)
  }

  class ViewHolder<T : Model<T>>(itemView: View) : MappingViewHolder<T>(itemView) {

    private val button: MaterialButton = itemView.findViewById(R.id.button)

    override fun bind(model: T) {
      button.text = model.title?.resolve(context)
      button.setOnClickListener {
        button.isEnabled = model.isEnabled && !model.disableOnClick
        model.onClick()
      }
      button.icon = model.icon?.resolve(context)
      button.isEnabled = model.isEnabled
    }
  }
}
