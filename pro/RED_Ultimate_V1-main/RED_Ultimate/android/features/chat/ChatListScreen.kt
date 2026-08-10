package com.red.sovereign.features.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.red.sovereign.features.stories.StoryListSection
import com.red.sovereign.features.stories.StoryViewModel

@Composable
fun ChatListScreen(
    navController: NavController,
    storyViewModel: StoryViewModel = hiltViewModel(),
    chatViewModel: ChatViewModel = hiltViewModel()
) {
    val stories by storyViewModel.activeStories.collectAsState()
    val chats by chatViewModel.getConversations().collectAsState(initial = emptyList())

    Scaffold(
        topBar = { TopAppBar(title = { Text("يونس") }) }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            // إضافة ميزة القصص برمجياً
            item {
                StoryListSection(
                    stories = stories,
                    onAddClick = { navController.navigate("create_story") },
                    onStoryClick = { userId -> navController.navigate("story_viewer/$userId") }
                )
            }
            
            // قائمة المحادثات
            items(chats) { chat ->
                ConversationItem(chat) { navController.navigate("chat_detail/${chat.id}") }
            }
        }
    }
}
