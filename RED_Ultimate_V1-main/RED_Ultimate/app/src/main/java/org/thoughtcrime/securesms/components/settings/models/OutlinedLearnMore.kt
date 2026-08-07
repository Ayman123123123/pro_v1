package com.red.sovereign.components.settings.models

import com.red.sovereign.components.settings.DSLSettingsText
import com.red.sovereign.components.settings.PreferenceModel
import com.red.sovereign.databinding.DslOutlinedLearnMoreBinding
import com.red.sovereign.util.adapter.mapping.BindingFactory
import com.red.sovereign.util.adapter.mapping.BindingViewHolder
import com.red.sovereign.util.adapter.mapping.MappingAdapter

/**
 * Show a informational text message in an outlined bubble.
 */
object OutlinedLearnMore {

  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(Model::class.java, BindingFactory(::ViewHolder, DslOutlinedLearnMoreBinding::inflate))
  }

  class Model(
    summary: DSLSettingsText,
    val learnMoreUrl: String
  ) : PreferenceModel<Model>(summary = summary) {
    override fun areContentsTheSame(newItem: Model): Boolean {
      return super.areContentsTheSame(newItem) && learnMoreUrl == newItem.learnMoreUrl
    }
  }

  private class ViewHolder(binding: DslOutlinedLearnMoreBinding) : BindingViewHolder<Model, DslOutlinedLearnMoreBinding>(binding) {
    override fun bind(model: Model) {
      binding.root.text = model.summary!!.resolve(context)
      binding.root.setLearnMoreVisible(true)
      binding.root.setLink(model.learnMoreUrl)
    }
  }
}
