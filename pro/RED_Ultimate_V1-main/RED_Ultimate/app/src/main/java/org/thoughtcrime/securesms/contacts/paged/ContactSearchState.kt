package com.red.sovereign.contacts.paged

import com.red.sovereign.conversationlist.chatfilter.ConversationFilterRequest
import com.red.sovereign.search.SearchFilter

/**
 * Simple search state for contacts.
 */
data class ContactSearchState(
  val query: String? = null,
  val conversationFilterRequest: ConversationFilterRequest? = null,
  val expandedSections: Set<ContactSearchConfiguration.SectionKey> = emptySet(),
  val groupStories: Set<ContactSearchData.Story> = emptySet(),
  val searchFilter: SearchFilter = SearchFilter.EMPTY
)
