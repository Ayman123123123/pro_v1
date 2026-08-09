package com.red.sovereign.database

internal interface RecipientIdDatabaseReference {
  fun remapRecipient(fromId: RecipientId?, toId: RecipientId?)
}
