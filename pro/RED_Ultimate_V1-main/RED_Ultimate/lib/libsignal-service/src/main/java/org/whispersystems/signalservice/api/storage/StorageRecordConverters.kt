/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.storage

import org.whispersystems.signalservice.internal.storage.protos.AccountRecord
import org.whispersystems.signalservice.internal.storage.protos.CallLinkRecord
import org.whispersystems.signalservice.internal.storage.protos.ChatFolderRecord
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord
import org.whispersystems.signalservice.internal.storage.protos.GroupV1Record
import org.whispersystems.signalservice.internal.storage.protos.GroupV2Record
import org.whispersystems.signalservice.internal.storage.protos.NotificationProfile
import org.whispersystems.signalservice.internal.storage.protos.StickerPackRecord
import org.whispersystems.signalservice.internal.storage.protos.StorageRecord
import org.whispersystems.signalservice.internal.storage.protos.StoryDistributionListRecord

fun ContactRecord.toREDContactRecord(storageId: StorageId): REDContactRecord {
  return REDContactRecord(storageId, this)
}

fun AccountRecord.toREDAccountRecord(storageId: StorageId): REDAccountRecord {
  return REDAccountRecord(storageId, this)
}

fun AccountRecord.Builder.toREDAccountRecord(storageId: StorageId): REDAccountRecord {
  return REDAccountRecord(storageId, this.build())
}

fun GroupV1Record.toREDGroupV1Record(storageId: StorageId): REDGroupV1Record {
  return REDGroupV1Record(storageId, this)
}

fun GroupV2Record.toREDGroupV2Record(storageId: StorageId): REDGroupV2Record {
  return REDGroupV2Record(storageId, this)
}

fun StoryDistributionListRecord.toREDStoryDistributionListRecord(storageId: StorageId): REDStoryDistributionListRecord {
  return REDStoryDistributionListRecord(storageId, this)
}

fun CallLinkRecord.toREDCallLinkRecord(storageId: StorageId): REDCallLinkRecord {
  return REDCallLinkRecord(storageId, this)
}

fun ChatFolderRecord.toREDChatFolderRecord(storageId: StorageId): REDChatFolderRecord {
  return REDChatFolderRecord(storageId, this)
}

fun NotificationProfile.toREDNotificationProfileRecord(storageId: StorageId): REDNotificationProfileRecord {
  return REDNotificationProfileRecord(storageId, this)
}

fun StickerPackRecord.toREDStickerPackRecord(storageId: StorageId): REDStickerPackRecord {
  return REDStickerPackRecord(storageId, this)
}

fun REDContactRecord.toREDStorageRecord(): REDStorageRecord {
  return REDStorageRecord(id, StorageRecord(contact = this.proto))
}

fun REDGroupV1Record.toREDStorageRecord(): REDStorageRecord {
  return REDStorageRecord(id, StorageRecord(groupV1 = this.proto))
}

fun REDGroupV2Record.toREDStorageRecord(): REDStorageRecord {
  return REDStorageRecord(id, StorageRecord(groupV2 = this.proto))
}

fun REDAccountRecord.toREDStorageRecord(): REDStorageRecord {
  return REDStorageRecord(id, StorageRecord(account = this.proto))
}

fun REDStoryDistributionListRecord.toREDStorageRecord(): REDStorageRecord {
  return REDStorageRecord(id, StorageRecord(storyDistributionList = this.proto))
}

fun REDCallLinkRecord.toREDStorageRecord(): REDStorageRecord {
  return REDStorageRecord(id, StorageRecord(callLink = this.proto))
}

fun REDChatFolderRecord.toREDStorageRecord(): REDStorageRecord {
  return REDStorageRecord(id, StorageRecord(chatFolder = this.proto))
}

fun REDNotificationProfileRecord.toREDStorageRecord(): REDStorageRecord {
  return REDStorageRecord(id, StorageRecord(notificationProfile = this.proto))
}

fun REDStickerPackRecord.toREDStorageRecord(): REDStorageRecord {
  return REDStorageRecord(id, StorageRecord(stickerPack = this.proto))
}
