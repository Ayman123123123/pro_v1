package com.red.sovereign.search

import com.red.sovereign.database.model.ThreadWithRecipient

data class ThreadSearchResult(val results: List<ThreadWithRecipient>, val query: String)
