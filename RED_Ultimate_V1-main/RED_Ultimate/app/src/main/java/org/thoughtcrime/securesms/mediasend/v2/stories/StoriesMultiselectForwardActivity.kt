package com.red.sovereign.mediasend.v2.stories

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.fragment.app.FragmentManager
import kotlinx.parcelize.Parcelize
import org.signal.core.util.getParcelableArrayListExtraCompat
import com.red.sovereign.contacts.paged.ArbitraryRepository
import com.red.sovereign.contacts.paged.ContactSearchConfiguration
import com.red.sovereign.contacts.paged.ContactSearchData
import com.red.sovereign.contacts.paged.ContactSearchKey
import com.red.sovereign.contacts.paged.ContactSearchState
import com.red.sovereign.conversation.mutiselect.forward.MultiselectForwardActivity
import com.red.sovereign.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import com.red.sovereign.stories.Stories
import com.red.sovereign.util.adapter.mapping.MappingModel
import com.red.sovereign.util.adapter.mapping.compose.MappingEntryProvider
import com.red.sovereign.util.adapter.mapping.compose.rememberMappingEntryProvider

class StoriesMultiselectForwardActivity : MultiselectForwardActivity() {

  companion object {
    private const val PREVIEW_MEDIA = "preview_media"
    private const val PREVIEW_ITEM = "preview_item"
  }

  override fun getSearchConfiguration(fragmentManager: FragmentManager, contactSearchState: ContactSearchState): ContactSearchConfiguration? {
    return ContactSearchConfiguration.build {
      query = contactSearchState.query

      addSection(
        ContactSearchConfiguration.Section.Arbitrary(setOf(PREVIEW_ITEM))
      )

      addSection(
        ContactSearchConfiguration.Section.Stories(
          groupStories = contactSearchState.groupStories,
          includeHeader = true,
          headerAction = Stories.getHeaderAction(fragmentManager)
        )
      )
    }
  }

  private val previewMedia: List<Uri> by lazy { intent.getParcelableArrayListExtraCompat(PREVIEW_MEDIA, Uri::class.java).orEmpty() }

  override fun getArbitraryRepository(): ArbitraryRepository {
    return object : ArbitraryRepository {
      override fun getSize(section: ContactSearchConfiguration.Section.Arbitrary, query: String?): Int = if (previewMedia.isEmpty()) 0 else 1

      override fun getData(
        section: ContactSearchConfiguration.Section.Arbitrary,
        query: String?,
        startIndex: Int,
        endIndex: Int,
        totalSearchSize: Int
      ): List<ContactSearchData.Arbitrary> {
        return if (previewMedia.isEmpty()) emptyList() else listOf(ContactSearchData.Arbitrary(PREVIEW_ITEM))
      }

      override fun getMappingModel(arbitrary: ContactSearchData.Arbitrary): MappingModel<*> {
        return PreviewEntryMappingModel()
      }
    }
  }

  @Composable
  override fun getAdditionalEntries(): MappingEntryProvider<Any> {
    return rememberMappingEntryProvider {
      entry<PreviewEntryMappingModel> {
        StoryMediaPreviews(previews = previewMedia)
      }
    }
  }

  private class PreviewEntryMappingModel : MappingModel<PreviewEntryMappingModel> {
    override fun areItemsTheSame(newItem: PreviewEntryMappingModel): Boolean = true
    override fun areContentsTheSame(newItem: PreviewEntryMappingModel): Boolean = true
  }

  class SelectionContract : ActivityResultContract<Args, List<ContactSearchKey.RecipientSearchKey>>() {

    private val multiselectContract = MultiselectForwardActivity.SelectionContract()

    override fun createIntent(context: Context, input: Args): Intent {
      return multiselectContract.createIntent(context, input.multiselectForwardFragmentArgs)
        .setClass(context, StoriesMultiselectForwardActivity::class.java)
        .putExtra(PREVIEW_MEDIA, ArrayList(input.previews))
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<ContactSearchKey.RecipientSearchKey> {
      return multiselectContract.parseResult(resultCode, intent)
    }
  }

  @Parcelize
  class Args(
    val multiselectForwardFragmentArgs: MultiselectForwardFragmentArgs,
    val previews: List<Uri>
  ) : Parcelable
}
