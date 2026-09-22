package com.red.sovereign.stories.settings.create

import androidx.navigation.fragment.findNavController
import com.red.sovereign.R
import com.red.sovereign.database.model.DistributionListId
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.stories.settings.select.BaseStoryRecipientSelectionFragment
import com.red.sovereign.util.navigation.safeNavigate

/**
 * Allows user to select who will see the story they are creating
 */
class CreateStoryViewerSelectionFragment : BaseStoryRecipientSelectionFragment() {
  override val actionButtonLabel: Int = R.string.CreateStoryViewerSelectionFragment__next
  override val distributionListId: DistributionListId? = null

  override fun goToNextScreen(recipients: Set<RecipientId>) {
    findNavController().safeNavigate(CreateStoryViewerSelectionFragmentDirections.actionCreateStoryViewerSelectionToCreateStoryWithViewers(recipients.toTypedArray()))
  }
}
