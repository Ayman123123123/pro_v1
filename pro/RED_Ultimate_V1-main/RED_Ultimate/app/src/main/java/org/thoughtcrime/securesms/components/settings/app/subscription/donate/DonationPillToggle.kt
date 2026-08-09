package com.red.sovereign.components.settings.app.subscription.donate

import com.google.android.material.button.MaterialButton
import org.signal.donations.InAppPaymentType
import com.red.sovereign.R
import com.red.sovereign.databinding.DonationPillToggleBinding
import com.red.sovereign.util.adapter.mapping.BindingFactory
import com.red.sovereign.util.adapter.mapping.BindingViewHolder
import com.red.sovereign.util.adapter.mapping.MappingAdapter
import com.red.sovereign.util.adapter.mapping.MappingModel

object DonationPillToggle {

  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(Model::class.java, BindingFactory(::ViewHolder, DonationPillToggleBinding::inflate))
  }

  class Model(
    val selected: InAppPaymentType,
    val onClick: () -> Unit
  ) : MappingModel<Model> {
    override fun areItemsTheSame(newItem: Model): Boolean = true

    override fun areContentsTheSame(newItem: Model): Boolean {
      return selected == newItem.selected
    }
  }

  private class ViewHolder(binding: DonationPillToggleBinding) : BindingViewHolder<Model, DonationPillToggleBinding>(binding) {
    override fun bind(model: Model) {
      when (model.selected) {
        InAppPaymentType.ONE_TIME_DONATION -> {
          presentButtons(model, binding.oneTime, binding.monthly)
        }
        InAppPaymentType.RECURRING_DONATION -> {
          presentButtons(model, binding.monthly, binding.oneTime)
        }
        else -> {
          error("Unsupported donation type.")
        }
      }
    }

    private fun presentButtons(model: Model, selected: MaterialButton, notSelected: MaterialButton) {
      selected.setOnClickListener(null)
      notSelected.setOnClickListener { model.onClick() }
      selected.isSelected = true
      notSelected.isSelected = false
      selected.setIconResource(R.drawable.ic_check_24)
      notSelected.icon = null
    }
  }
}
