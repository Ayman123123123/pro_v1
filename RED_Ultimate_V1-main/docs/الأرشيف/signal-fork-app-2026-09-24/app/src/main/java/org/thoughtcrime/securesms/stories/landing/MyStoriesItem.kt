package com.red.sovereign.stories.landing

import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.red.sovereign.R
import com.red.sovereign.avatar.view.AvatarView
import com.red.sovereign.components.settings.PreferenceModel
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.util.adapter.mapping.LayoutFactory
import com.red.sovereign.util.adapter.mapping.MappingAdapter
import com.red.sovereign.util.adapter.mapping.MappingViewHolder

/**
 * Item displayed on an empty Stories landing page allowing the user to add a new story.
 */
object MyStoriesItem {

  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(Model::class.java, LayoutFactory(::ViewHolder, R.layout.stories_landing_item_my_stories))
  }

  class Model(
    val lifecycleOwner: LifecycleOwner,
    val onClick: () -> Unit
  ) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean = true
  }

  private class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val avatarView: AvatarView = itemView.findViewById(R.id.avatar)

    private var recipient: Recipient? = null

    private val recipientObserver = object : Observer<Recipient> {
      override fun onChanged(recipient: Recipient) {
        onRecipientChanged(recipient)
      }
    }

    override fun bind(model: Model) {
      itemView.setOnClickListener { model.onClick() }
      observeRecipient(model.lifecycleOwner, Recipient.self())
    }

    private fun onRecipientChanged(recipient: Recipient) {
      avatarView.displayProfileAvatar(recipient)
    }

    private fun observeRecipient(lifecycleOwner: LifecycleOwner?, recipient: Recipient?) {
      this.recipient?.live()?.liveData?.removeObserver(recipientObserver)

      this.recipient = recipient

      lifecycleOwner?.let {
        this.recipient?.live()?.liveData?.observe(lifecycleOwner, recipientObserver)
      }
    }

    override fun onViewRecycled() {
      unbindRecipient()
    }

    private fun unbindRecipient() {
      observeRecipient(null, null)
    }
  }
}
