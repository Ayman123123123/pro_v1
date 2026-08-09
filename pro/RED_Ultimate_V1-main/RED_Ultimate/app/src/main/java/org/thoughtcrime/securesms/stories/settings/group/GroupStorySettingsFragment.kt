package com.red.sovereign.stories.settings.group

import android.view.MenuItem
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.dp
import com.red.sovereign.R
import com.red.sovereign.components.menu.ActionItem
import com.red.sovereign.components.menu.REDContextMenu
import com.red.sovereign.components.settings.DSLConfiguration
import com.red.sovereign.components.settings.DSLSettingsFragment
import com.red.sovereign.components.settings.DSLSettingsText
import com.red.sovereign.components.settings.configure
import com.red.sovereign.conversation.ConversationIntents
import com.red.sovereign.stories.dialogs.StoryDialogs
import com.red.sovereign.stories.settings.custom.PrivateStoryItem
import com.red.sovereign.util.adapter.mapping.MappingAdapter
import org.signal.core.ui.R as CoreUiR

/**
 * Displays who can see a group story and gives the user an option to remove it.
 */
class GroupStorySettingsFragment : DSLSettingsFragment(menuId = R.menu.story_group_menu) {

  private val lifecycleDisposable = LifecycleDisposable()

  private val viewModel: GroupStorySettingsViewModel by viewModels(factoryProducer = {
    GroupStorySettingsViewModel.Factory(GroupStorySettingsFragmentArgs.fromBundle(requireArguments()).groupId)
  })

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    val toolbar: Toolbar = requireView().findViewById(R.id.toolbar)
    if (item.itemId == R.id.action_overflow) {
      REDContextMenu.Builder(toolbar, requireView() as ViewGroup)
        .preferredHorizontalPosition(REDContextMenu.HorizontalPosition.END)
        .preferredVerticalPosition(REDContextMenu.VerticalPosition.BELOW)
        .offsetX(16.dp)
        .offsetY((-4).dp)
        .show(
          listOf(
            ActionItem(
              iconRes = R.drawable.ic_open_24_tinted,
              title = getString(R.string.StoriesLandingItem__go_to_chat),
              action = {
                lifecycleDisposable += viewModel.getConversationData().flatMap { data ->
                  ConversationIntents.createBuilder(requireContext(), data.groupRecipientId, data.groupThreadId)
                }.subscribeBy {
                  startActivity(it.build())
                }
              }
            )
          )
        )
    }
    return super.onOptionsItemSelected(item)
  }

  override fun bindAdapter(adapter: MappingAdapter) {
    PrivateStoryItem.register(adapter)

    lifecycleDisposable.bindTo(viewLifecycleOwner)
    viewModel.state.observe(viewLifecycleOwner) { state ->
      if (state.removed) {
        findNavController().popBackStack()
        return@observe
      }

      setTitle(state.name)
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }
  }

  private fun getConfiguration(state: GroupStorySettingsState): DSLConfiguration {
    return configure {
      sectionHeaderPref(R.string.GroupStorySettingsFragment__who_can_view_this_story)

      state.members.forEach {
        customPref(PrivateStoryItem.RecipientModel(it))
      }

      textPref(
        title = DSLSettingsText.from(
          getString(R.string.GroupStorySettingsFragment__members_of_the_group_s, state.name),
          DSLSettingsText.TextAppearanceModifier(CoreUiR.style.RED_Text_BodyMedium),
          DSLSettingsText.ColorModifier(ContextCompat.getColor(requireContext(), CoreUiR.color.signal_colorOnSurfaceVariant))
        )
      )

      dividerPref()

      clickPref(
        title = DSLSettingsText.from(
          R.string.GroupStorySettingsFragment__remove_group_story,
          DSLSettingsText.ColorModifier(ContextCompat.getColor(requireContext(), CoreUiR.color.signal_colorError))
        ),
        onClick = {
          StoryDialogs.removeGroupStory(
            requireContext(),
            viewModel.titleSnapshot
          ) {
            viewModel.doNotDisplayAsStory()
          }
        }
      )
    }
  }
}
