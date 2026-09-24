package com.red.sovereign.stories.settings.my

import com.red.sovereign.R
import com.red.sovereign.databinding.AllREDConnectionsRowItemBinding
import com.red.sovereign.util.adapter.mapping.BindingFactory
import com.red.sovereign.util.adapter.mapping.BindingViewHolder
import com.red.sovereign.util.adapter.mapping.MappingAdapter
import com.red.sovereign.util.adapter.mapping.MappingModel
import com.red.sovereign.util.visible

/**
 * AllREDConnections privacy setting row item with "View" support
 */
object AllREDConnectionsRowItem {

  private const val IS_CHECKED = 0
  private const val IS_COUNT = 1

  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(Model::class.java, BindingFactory(::ViewHolder, AllREDConnectionsRowItemBinding::inflate))
  }

  class Model(
    val isChecked: Boolean,
    val count: Int,
    val onRowClicked: () -> Unit,
    val onViewClicked: () -> Unit
  ) : MappingModel<Model> {

    override fun areItemsTheSame(newItem: Model): Boolean = true

    override fun areContentsTheSame(newItem: Model): Boolean = isChecked == newItem.isChecked && count == newItem.count

    override fun getChangePayload(newItem: Model): Any? {
      val isCheckedDifferent = isChecked != newItem.isChecked
      val isCountDifferent = count != newItem.count

      return when {
        isCheckedDifferent && !isCountDifferent -> IS_CHECKED
        !isCheckedDifferent && isCountDifferent -> IS_COUNT
        else -> null
      }
    }
  }

  private class ViewHolder(binding: AllREDConnectionsRowItemBinding) : BindingViewHolder<Model, AllREDConnectionsRowItemBinding>(binding) {
    override fun bind(model: Model) {
      binding.root.setOnClickListener { model.onRowClicked() }
      binding.view.setOnClickListener { model.onViewClicked() }

      when {
        payload.contains(IS_COUNT) -> presentCount(model.count)
        payload.contains(IS_CHECKED) -> presentSelected(model.isChecked)
        else -> {
          presentCount(model.count)
          presentSelected(model.isChecked)
        }
      }
    }

    private fun presentCount(count: Int) {
      binding.count.visible = count > 0
      binding.count.text = context.resources.getQuantityString(R.plurals.MyStorySettingsFragment__viewers, count, count)
    }

    private fun presentSelected(isChecked: Boolean) {
      binding.radio.isChecked = isChecked
    }
  }
}
