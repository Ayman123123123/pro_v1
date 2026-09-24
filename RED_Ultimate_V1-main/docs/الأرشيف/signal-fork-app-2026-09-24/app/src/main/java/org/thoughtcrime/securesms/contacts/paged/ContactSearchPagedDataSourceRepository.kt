package com.red.sovereign.contacts.paged

import android.content.Context
import android.database.Cursor
import androidx.annotation.WorkerThread
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.signal.core.util.CursorUtil
import org.signal.core.util.LRUCache
import com.red.sovereign.R
import com.red.sovereign.contacts.ContactRepository
import com.red.sovereign.contacts.paged.collections.ContactSearchIterator
import com.red.sovereign.database.DistributionListTables
import com.red.sovereign.database.GroupTable
import com.red.sovereign.database.RecipientTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.ThreadTable
import com.red.sovereign.database.model.DistributionListPrivacyMode
import com.red.sovereign.database.model.GroupRecord
import com.red.sovereign.groups.GroupId
import com.red.sovereign.groups.GroupsInCommonRepository
import com.red.sovereign.groups.GroupsInCommonSummary
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.keyvalue.StorySend
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId

/**
 * Database boundary interface which allows us to safely unit test the data source without
 * having to deal with database access.
 */
open class ContactSearchPagedDataSourceRepository(
  context: Context,
  selfTitle: String = context.getString(R.string.note_to_self)
) {

  private val contactRepository = ContactRepository(selfTitle)
  private val context = context.applicationContext
  private val groupRecordCache = LRUCache<GroupId, GroupRecord?>(100)

  open fun getLatestStorySends(activeStoryCutoffDuration: Long): List<StorySend> {
    return REDStore.story
      .getLatestActiveStorySendTimestamps(System.currentTimeMillis() - activeStoryCutoffDuration)
  }

  open fun queryREDContacts(contactsSearchQuery: RecipientTable.ContactSearchQuery): Cursor? {
    return contactRepository.queryREDContacts(contactsSearchQuery)
  }

  open fun queryGroupMemberContacts(section: ContactSearchConfiguration.Section.GroupMembers, query: String?): Cursor? {
    return contactRepository.queryGroupMemberContacts(query ?: "", section.groupId)
  }

  open fun getGroupSearchIterator(
    section: ContactSearchConfiguration.Section.Groups,
    query: String?
  ): ContactSearchIterator<GroupRecord> {
    return REDDatabase.groups.queryGroups(
      GroupTable.GroupQuery.Builder()
        .withSearchQuery(query)
        .withInactiveGroups(section.includeInactive)
        .withMmsGroups(section.includeMms)
        .withV1Groups(section.includeV1)
        .withSortOrder(section.sortOrder)
        .build()
    )
  }

  open fun getRecents(section: ContactSearchConfiguration.Section.Recents): Cursor? {
    return REDDatabase.threads.getRecentConversationList(
      section.limit,
      section.includeInactiveGroups,
      section.mode == ContactSearchConfiguration.Section.Recents.Mode.INDIVIDUALS,
      section.mode == ContactSearchConfiguration.Section.Recents.Mode.GROUPS,
      !section.includeGroupsV1,
      !section.includeSms,
      !section.includeSelf
    )
  }

  open fun getStories(query: String?): Cursor? {
    return REDDatabase.distributionLists.getAllListsForContactSelectionUiCursor(query, myStoryContainsQuery(query ?: ""))
  }

  open fun getGroupsWithMembers(query: String): Cursor {
    return REDDatabase.groups.queryGroupsByMemberName(query)
  }

  open fun getContactsWithoutThreads(query: String): Cursor {
    return REDDatabase.recipients.getAllContactsWithoutThreads(query)
  }

  open fun getRecipientFromDistributionListCursor(cursor: Cursor): Recipient {
    return Recipient.resolved(RecipientId.from(CursorUtil.requireLong(cursor, DistributionListTables.RECIPIENT_ID)))
  }

  open fun getPrivacyModeFromDistributionListCursor(cursor: Cursor): DistributionListPrivacyMode {
    return DistributionListPrivacyMode.deserialize(CursorUtil.requireLong(cursor, DistributionListTables.PRIVACY_MODE))
  }

  open fun getRecipientFromThreadCursor(cursor: Cursor): Recipient {
    return Recipient.resolved(RecipientId.from(CursorUtil.requireLong(cursor, ThreadTable.RECIPIENT_ID)))
  }

  open fun getRecipientFromSearchCursor(cursor: Cursor): Recipient {
    return Recipient.resolved(RecipientId.from(CursorUtil.requireLong(cursor, ContactRepository.ID_COLUMN)))
  }

  open fun getRecipientFromRecipientCursor(cursor: Cursor): Recipient {
    return Recipient.resolved(RecipientId.from(CursorUtil.requireLong(cursor, RecipientTable.ID)))
  }

  @WorkerThread
  open fun getGroupsInCommon(recipient: Recipient): GroupsInCommonSummary {
    return runBlocking {
      GroupsInCommonRepository
        .getGroupsInCommonSummary(context, recipient.id)
        .first()
    }
  }

  open fun getRecipientFromGroupRecord(groupRecord: GroupRecord): Recipient {
    return Recipient.resolved(groupRecord.recipientId)
  }

  open fun getDistributionListMembershipCount(recipient: Recipient): Int {
    return REDDatabase.distributionLists.getMemberCount(recipient.requireDistributionListId())
  }

  open fun getGroupStories(): Set<ContactSearchData.Story> {
    return REDDatabase.groups.getGroupsToDisplayAsStories().map {
      val recipient = Recipient.resolved(REDDatabase.recipients.getOrInsertFromGroupId(it))
      ContactSearchData.Story(recipient, recipient.participantIds.size, DistributionListPrivacyMode.ALL)
    }.toSet()
  }

  open fun recipientNameContainsQuery(recipient: Recipient, query: String?): Boolean {
    return query.isNullOrBlank() || recipient.getDisplayName(context).contains(query, ignoreCase = true)
  }

  open fun myStoryContainsQuery(query: String): Boolean {
    if (query.isEmpty()) {
      return true
    }

    val myStory = context.getString(R.string.Recipient_my_story)
    return myStory.contains(query, ignoreCase = true)
  }

  open fun getGroupRecord(groupId: GroupId): GroupRecord? {
    if (!groupRecordCache.containsKey(groupId)) {
      groupRecordCache[groupId] = REDDatabase.groups.getGroup(groupId).orElse(null)
    }
    return groupRecordCache[groupId]
  }

  open fun clearGroupRecordCache() {
    groupRecordCache.clear()
  }
}
