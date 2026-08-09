package com.red.sovereign.features.stories

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun StoryListSection(
    stories: List<StoryEntity>,
    onAddClick: () -> Unit,
    onStoryClick: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // إضافة قصة جديدة
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier.size(60.dp).clickable { onAddClick() },
                    shape = CircleShape,
                    color = Color.DarkGray
                ) { Icon(Icons.Default.Add, null, modifier = Modifier.padding(16.dp), tint = Color.White) }
                Text("Your Story", style = MaterialTheme.typography.labelSmall)
            }
        }
        
        // عرض قصص جهات الاتصال
        items(stories.distinctBy { it.userId }) { story ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(
                    model = story.mediaUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .border(2.dp, Color.Red, CircleShape)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .clickable { onStoryClick(story.userId) },
                    contentScale = ContentScale.Crop
                )
                Text("RED User", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
