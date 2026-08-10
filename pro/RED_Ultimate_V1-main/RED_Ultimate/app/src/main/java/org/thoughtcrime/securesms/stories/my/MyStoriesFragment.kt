package com.red.sovereign.stories.my

import android.net.Uri
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.signal.core.ui.permissions.Permissions
import org.signal.core.util.concurrent.LifecycleDisposable
import com.red.sovereign.R
import com.red.sovereign.components.settings.DSLConfiguration
import com.red.sovereign.components.settings.DSLSettingsFragment
import com.red.sovereign.components.settings.DSLSettingsText
import com.red.sovereign.components.settings.configure
import com.red.sovereign.conversation.mutiselect.forward.MultiselectForwardFragment
import com.red.sovereign.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import com.red.sovereign.database.model.MmsMessageRecord
import com.red.sovereign.main.MainNavigationViewModel
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.safety.SafetyNumberBottomSheet
import com.red.sovereign.stories.StoryTextPostModel
import com.red.sovereign.stories.StoryViewerArgs
import com.red.sovereign.stories.dialogs.StoryContextMenu
import com.red.sovereign.stories.dialogs.StoryDialogs
import com.red.sovereign.stories.viewer.StoryViewerActivity
import com.red.sovereign.util.adapter.mapping.MappingAdapter
import com.red.sovereign.util.visible

class MyStoriesFragment : DSLSettingsFragment(
  layoutId = R.layout.stories_my_stories_fragment,
  titleId = R.string.StoriesLandingFragment__my_stories
) {

  private val lifecycleDisposable = LifecycleDisposable()

  private val mainNavigationViewModel: MainNavigationViewModel by activityViewModels()

  private val viewModel: MyStoriesViewModel by viewModels(
    factoryProducer = {
      MyStoriesViewModel.Factory(MyStoriesRepository(requireContext()))
    }
  )

  override fun bindAdapter(adapter: MappingAdapter) {
    MyStoriesItem.register(adapter)

    requireActivity().onBackPressedDispatcher.addCallback(
      viewLifecycleOwner,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          mainNavigationViewModel.popStoriesDetailLocation()
        }
      }
    )

    val emptyNotice = requireView().findViewById<View>(R.id.empty_notice)
    lifecycleDisposable.bindTo(viewLifecycleOwner)
    viewModel.state.observe(viewLifecycleOwner) {
      adapter.submitList(getConfiguration(it).toMappingModelList())
      emptyNotice.visible = it.distributionSets.isEmpty()
    }
  }

  private fun getConfiguration(state: MyStoriesState): DSLConfiguration {
    return configure {
      val nonEmptySets = state.distributionSets.filter { it.stories.isNotEmpty() }
      nonEmptySets
        .forEachIndexed { index, distributionSet ->
          sectionHeaderPref(
            if (distributionSet.label == null) {
              DSLSettingsText.from(getString(R.string.MyStories__ss_story, Recipient.self().getShortDisplayName(requireContext())))
            } else {
              DSLSettingsText.from(distributionSet.label)
            }
          )
          distributionSet.stories.forEach { distributionStory ->
            customPref(
              MyStoriesItem.Model(
                distributionStory = distributionStory,
                onClick = { it, preview ->
                  openStoryViewer(it, preview, false)
                },
                onSaveClick = {
                  lifecycleScope.launch {
                    StoryContextMenu.save(
                      fragment = this@MyStoriesFragment,
                      messageRecord = it.distributionStory.messageRecord
                    )
                  }
                },
                onDeleteClick = this@MyStoriesFragment::handleDeleteClick,
                onForwardClick = { item ->
                  MultiselectForwardFragmentArgs.create(
                    requireContext(),
                    item.distributionStory.message.multiselectCollection.toSet()
                  ) {
                    MultiselectForwardFragment.showBottomSheet(childFragmentManager, it)
                  }
                },
                onShareClick = {
                  StoryContextMenu.share(this@MyStoriesFragment, it.distributionStory.messageRecord as MmsMessageRecord)
                },
                onInfoClick = { model, preview ->
                  openStoryViewer(model, preview, true)
                }
              )
            )
          }

          if (index != nonEmptySets.lastIndex) {
            dividerPref()
          }
        }
    }
  }

  private fun openStoryViewer(it: MyStoriesItem.Model, preview: View, isFromInfoContextMenuAction: Boolean) {
    if (it.distributionStory.messageRecord.isOutgoing && it.distributionStory.messageRecord.isFailed) {
      if (it.distributionStory.messageRecord.isIdentityMismatchFailure) {
        SafetyNumberBottomSheet
          .forOutgoingMessageRecord(requireContext(), it.distributionStory.messageRecord)
          .show(childFragmentManager)
      } else {
        StoryDialogs.resendStory(requireContext()) {
          lifecycleDisposable += viewModel.resend(it.distributionStory.messageRecord).subscribe()
        }
      }
    } else {
      val recipient = if (it.distributionStory.messageRecord.toRecipient.isGroup) {
        it.distributionStory.messageRecord.toRecipient
      } else {
        Recipient.self()
      }

      val record = it.distributionStory.messageRecord as MmsMessageRecord
      val blur = record.slideDeck.thumbnailSlide?.placeholderBlur
      val (text: StoryTextPostModel?, image: Uri?) = if (record.storyType.isTextStory) {
        StoryTextPostModel.parseFrom(record) to null
      } else {
        null to record.slideDeck.thumbnailSlide?.uri
      }

      val options = ActivityOptionsCompat.makeSceneTransitionAnimation(requireActivity(), preview, ViewCompat.getTransitionName(preview) ?: "")
      startActivity(
        StoryViewerActivity.createIntent(
          context = requireContext(),
          storyViewerArgs = StoryViewerArgs(
            recipientId = recipient.id,
            storyId = it.distributionStory.messageRecord.id,
            isInHiddenStoryMode = recipient.shouldHideStory,
            storyThumbTextModel = text,
            storyThumbUri = image,
            storyThumbBlur = blur,
            isFromInfoContextMenuAction = isFromInfoContextMenuAction,
            isFromMyStories = true
          )
        ),
        options.toBundle()
      )
    }
  }

  private fun handleDeleteClick(model: MyStoriesItem.Model) {
    lifecycleDisposable += StoryContextMenu.delete(requireContext(), model.distributionStory.messageRecord).subscribe()
  }

  @Suppress("OVERRIDE_DEPRECATION")
  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
  }
}
