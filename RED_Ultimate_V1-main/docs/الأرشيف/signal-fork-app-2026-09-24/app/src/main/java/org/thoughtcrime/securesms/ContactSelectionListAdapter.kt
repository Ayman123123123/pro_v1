package com.red.sovereign

import android.content.Context
import com.red.sovereign.ContactSelectionListModels.FindByPhoneNumberModel
import com.red.sovereign.ContactSelectionListModels.FindByUsernameModel
import com.red.sovereign.ContactSelectionListModels.FindContactsBannerModel
import com.red.sovereign.ContactSelectionListModels.FindContactsModel
import com.red.sovereign.ContactSelectionListModels.InviteToREDModel
import com.red.sovereign.ContactSelectionListModels.MoreHeaderModel
import com.red.sovereign.ContactSelectionListModels.NewGroupModel
import com.red.sovereign.ContactSelectionListModels.RefreshContactsModel
import com.red.sovereign.contacts.paged.ContactSearchAdapter
import com.red.sovereign.contacts.paged.ContactSearchConfiguration
import com.red.sovereign.contacts.paged.ContactSearchData
import com.red.sovereign.contacts.paged.ContactSearchKey
import com.red.sovereign.util.adapter.mapping.MappingModel

class ContactSelectionListAdapter(
  context: Context,
  fixedContacts: Set<ContactSearchKey>,
  displayOptions: DisplayOptions,
  onClickCallbacks: OnContactSelectionClick,
  longClickCallbacks: LongClickCallbacks,
  storyContextMenuCallbacks: StoryContextMenuCallbacks,
  callButtonClickCallbacks: CallButtonClickCallbacks
) : ContactSearchAdapter(context, fixedContacts, displayOptions, onClickCallbacks, longClickCallbacks, storyContextMenuCallbacks, callButtonClickCallbacks) {

  init {
    ContactSelectionListModels.registerNewGroup(this, onClickCallbacks::onNewGroupClicked)
    ContactSelectionListModels.registerInviteToRED(this, onClickCallbacks::onInviteToREDClicked)
    ContactSelectionListModels.registerFindContacts(this, onClickCallbacks::onFindContactsClicked)
    ContactSelectionListModels.registerFindContactsBanner(this, onClickCallbacks::onDismissFindContactsBannerClicked, onClickCallbacks::onFindContactsClicked)
    ContactSelectionListModels.registerRefreshContacts(this, onClickCallbacks::onRefreshContactsClicked)
    ContactSelectionListModels.registerMoreHeader(this)
    ContactSelectionListModels.registerEmpty(this)
    ContactSelectionListModels.registerFindByUsername(this, onClickCallbacks::onFindByUsernameClicked)
    ContactSelectionListModels.registerFindByPhoneNumber(this, onClickCallbacks::onFindByPhoneNumberClicked)
  }

  class ArbitraryRepository : com.red.sovereign.contacts.paged.ArbitraryRepository {

    override fun getSize(section: ContactSearchConfiguration.Section.Arbitrary, query: String?): Int {
      return section.types.size
    }

    override fun getData(section: ContactSearchConfiguration.Section.Arbitrary, query: String?, startIndex: Int, endIndex: Int, totalSearchSize: Int): List<ContactSearchData.Arbitrary> {
      check(section.types.size == 1)
      return listOf(ContactSearchData.Arbitrary(section.types.first()))
    }

    override fun getMappingModel(arbitrary: ContactSearchData.Arbitrary): MappingModel<*> {
      return when (ContactSelectionListModels.ArbitraryRow.fromCode(arbitrary.type)) {
        ContactSelectionListModels.ArbitraryRow.NEW_GROUP -> NewGroupModel()
        ContactSelectionListModels.ArbitraryRow.INVITE_TO_SIGNAL -> InviteToREDModel()
        ContactSelectionListModels.ArbitraryRow.MORE_HEADING -> MoreHeaderModel()
        ContactSelectionListModels.ArbitraryRow.REFRESH_CONTACTS -> RefreshContactsModel()
        ContactSelectionListModels.ArbitraryRow.FIND_CONTACTS -> FindContactsModel()
        ContactSelectionListModels.ArbitraryRow.FIND_CONTACTS_BANNER -> FindContactsBannerModel()
        ContactSelectionListModels.ArbitraryRow.FIND_BY_PHONE_NUMBER -> FindByPhoneNumberModel()
        ContactSelectionListModels.ArbitraryRow.FIND_BY_USERNAME -> FindByUsernameModel()
      }
    }
  }

  interface OnContactSelectionClick : ClickCallbacks {
    fun onNewGroupClicked()
    fun onInviteToREDClicked()
    fun onRefreshContactsClicked()
    fun onFindContactsClicked()
    fun onDismissFindContactsBannerClicked()
    fun onFindByPhoneNumberClicked()
    fun onFindByUsernameClicked()
  }
}
