package com.red.sovereign.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.crypto.DecryptedMessage
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures

import com.red.sovereign.ui.theme.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatThreadScreen(
    contactName: String,
    contactStatus: String = "متصل ومؤمّن",
    messages: List<DecryptedMessage>,
    currentRedId: String,
    onNavigateBack: () -> Unit,
    onAudioCallClick: () -> Unit,
    onVideoCallClick: () -> Unit,
    onReplyClick: (DecryptedMessage) -> Unit = {},
    isTyping: Boolean = false,
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = YounesVoid,
        topBar = {
            ChatThreadTopBar(
                contactName = contactName,
                contactStatus = contactStatus,
                onNavigateBack = onNavigateBack,
                onAudioCallClick = onAudioCallClick,
                onVideoCallClick = onVideoCallClick
            )
        },
        bottomBar = bottomBar
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    isMine = message.outgoing,
                    onReplyClick = { onReplyClick(message) }
                )
            }
            if (isTyping) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        androidx.compose.material3.Card(
                            modifier = Modifier.padding(vertical = 4.dp),
                            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = YounesSurface),
                            shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 5.dp)
                        ) {
                            val lottieComposition by com.airbnb.lottie.compose.rememberLottieComposition(
                                com.airbnb.lottie.compose.LottieCompositionSpec.RawRes(com.red.sovereign.R.raw.typing_dots)
                            )
                            com.airbnb.lottie.compose.LottieAnimation(
                                composition = lottieComposition,
                                iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever,
                                modifier = Modifier.width(60.dp).height(30.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatThreadTopBar(
    contactName: String,
    contactStatus: String,
    onNavigateBack: () -> Unit,
    onAudioCallClick: () -> Unit,
    onVideoCallClick: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contactName,
                        color = YounesOnSurface,
                        fontSize = 18.sp,
                        fontFamily = CairoFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "E2EE",
                        tint = YounesPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = contactStatus,
                    color = YounesPrimary.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontFamily = TajawalFamily
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "عودة",
                    tint = YounesOnSurface
                )
            }
        },
        actions = {
            IconButton(onClick = onAudioCallClick) {
                Icon(
                    imageVector = Icons.Rounded.Call,
                    contentDescription = "مكالمة صوتية",
                    tint = YounesOnSurface
                )
            }
            IconButton(onClick = onVideoCallClick) {
                Icon(
                    imageVector = Icons.Rounded.Videocam,
                    contentDescription = "مكالمة فيديو",
                    tint = YounesOnSurface
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = YounesSurface.copy(alpha = 0.85f), // Glassmorphism feel
            scrolledContainerColor = YounesSurface
        )
    )
}

@Composable
fun MessageBubble(
    message: DecryptedMessage,
    isMine: Boolean,
    onReplyClick: () -> Unit = {}
) {
    // Bubble colors based on master plan
    val backgroundBrush = if (isMine) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF005C48), // Deep Emerald
                Color(0xFF007D63)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF182430), // Premium Carbon
                Color(0xFF182430)
            )
        )
    }

    val shape = if (isMine) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }

    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    val messageTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(message.timestamp), ZoneId.systemDefault())

    var showMenu by remember { mutableStateOf(false) }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Box {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(shape)
                    .background(backgroundBrush)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { showMenu = true }
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
                    Text(
                        text = String(message.plaintext, Charsets.UTF_8),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontFamily = TajawalFamily,
                        lineHeight = 22.sp
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = messageTime.format(formatter),
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            fontFamily = TajawalFamily
                        )
                        
                        if (isMine) {
                            Spacer(modifier = Modifier.width(4.dp))
                            val isRead = message.status == "READ" || message.status == "DELIVERED"
                            Text(
                                text = if (isRead) "✓✓" else "✓",
                                color = if (message.status == "READ") YounesPrimary else Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                fontFamily = TajawalFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("رد") },
                    onClick = {
                        showMenu = false
                        onReplyClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("نسخ") },
                    onClick = {
                        showMenu = false
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(String(message.plaintext, Charsets.UTF_8)))
                    }
                )
            }
        }
    }
}
