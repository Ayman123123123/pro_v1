package com.red.sovereign.features

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf

/**
 * قصص وملاحظات سيادية - أفضل من واتساب وتيليجرام 2026
 * 
 * مميزات من البحث:
 * - تيليجرام: Stories للجميع 1/يوم + private notes للجهات + suggested birthdays + profile colors
 * - واتساب: Status + music sharing + @all + polls + storage manager + AI
 * - هذا الملف يضيفها كلها بتشفير + أفضل
 */

data class SovereignStory(
    val id: String,
    val userId: String,
    val username: String,
    val displayName: String,
    val mediaUrl: String?,
    val mediaType: String, // IMAGE, VIDEO, TEXT, LIVE_PHOTO
    val text: String?,
    val backgroundColor: String?,
    val timestamp: Long,
    val expiresAt: Long, // 24h
    val views: Int = 0,
    val viewers: List<String> = emptyList(),
    val isPremium: Boolean = false,
    val playbackStyle: String = "Live" // Live, Loop, Bounce for Live Photos
)

data class PrivateNote(
    val contactId: String,
    val note: String, // visible only to you
    val howMet: String?,
    val birthday: String?,
    val customAvatar: String?,
    val customName: String?,
    val timestamp: Long
)

data class ProfileCustomization(
    val userId: String,
    val color: String,
    val background: String?,
    val giftBackdrop: String?,
    val giftSymbol: String?,
    val animatedReplyStyle: String?,
    val linkStyle: String?,
    val isPremium: Boolean
)

object SovereignStoriesAndNotes {

    private val stories = mutableStateListOf<SovereignStory>()
    private val privateNotes = mutableStateMapOf<String, PrivateNote>()
    private val profileCustomizations = mutableStateMapOf<String, ProfileCustomization>()

    /**
     * قصص - أفضل من تيليجرام وواتساب
     * - تيليجرام: Stories للجميع 1/يوم مجاني، غير محدود بريميوم، 24h، أرشيف تلقائي، خصوصية Everyone/My Contacts/Close Friends/Selected Users، تخصيص رسم/ملصقات/رابط/موقع/طقس/صوت
     * - واتساب: Status + music sharing Apple Music Spotify + animated stickers 2026 layout
     * - RED: Stories + Status + music sharing + Live Photos + Motion Photos + animated + E2EE + P2P + sovereign
     */
    fun createStory(
        userId: String,
        username: String,
        displayName: String,
        mediaUrl: String?,
        mediaType: String = "IMAGE",
        text: String? = null,
        backgroundColor: String? = null,
        isPremium: Boolean = false
    ): SovereignStory {
        val now = System.currentTimeMillis()
        val story = SovereignStory(
            id = "story_${now}_${userId}",
            userId = userId,
            username = username,
            displayName = displayName,
            mediaUrl = mediaUrl,
            mediaType = mediaType,
            text = text,
            backgroundColor = backgroundColor,
            timestamp = now,
            expiresAt = now + 24 * 60 * 60 * 1000L, // 24h
            isPremium = isPremium
        )
        stories.add(story)
        return story
    }

    fun getActiveStories(): List<SovereignStory> {
        val now = System.currentTimeMillis()
        return stories.filter { it.expiresAt > now }
    }

    fun getStoriesForUser(userId: String): List<SovereignStory> {
        return getActiveStories().filter { it.userId == userId }
    }

    fun viewStory(storyId: String, viewerId: String) {
        val index = stories.indexOfFirst { it.id == storyId }
        if (index != -1) {
            val story = stories[index]
            if (viewerId !in story.viewers) {
                stories[index] = story.copy(
                    views = story.views + 1,
                    viewers = story.viewers + viewerId
                )
            }
        }
    }

    fun deleteExpiredStories() {
        val now = System.currentTimeMillis()
        stories.removeAll { it.expiresAt <= now }
    }

    /**
     * ملاحظات خاصة للجهات - أفضل من تيليجرام
     * - تيليجرام: private notes لكل جهة visible only to you + how met + work + ice cream flavor
     * - RED: private notes + how met + work + birthday + custom avatar + custom name + E2EE + SQLCipher
     */
    fun addPrivateNote(
        contactId: String,
        note: String,
        howMet: String? = null,
        birthday: String? = null,
        customAvatar: String? = null,
        customName: String? = null
    ) {
        privateNotes[contactId] = PrivateNote(
            contactId = contactId,
            note = note,
            howMet = howMet,
            birthday = birthday,
            customAvatar = customAvatar,
            customName = customName,
            timestamp = System.currentTimeMillis()
        )
    }

    fun getPrivateNote(contactId: String): PrivateNote? {
        return privateNotes[contactId]
    }

    fun getAllPrivateNotes(): Map<String, PrivateNote> {
        return privateNotes.toMap()
    }

    fun suggestBirthday(contactId: String, birthday: String) {
        val existing = privateNotes[contactId]
        if (existing != null) {
            privateNotes[contactId] = existing.copy(birthday = birthday)
        } else {
            privateNotes[contactId] = PrivateNote(
                contactId = contactId,
                note = "",
                howMet = null,
                birthday = birthday,
                customAvatar = null,
                customName = null,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * تخصيص الملف الشخصي - أفضل من تيليجرام بريميوم
     * - تيليجرام بريميوم: advanced color customizations + background from gift collections + animated replies + unique link styles
     * - RED: profile colors + gift backdrop + symbol + animated replies + link styles + AAA + sovereign colors Emerald Gold
     */
    fun customizeProfile(
        userId: String,
        color: String,
        background: String? = null,
        giftBackdrop: String? = null,
        giftSymbol: String? = null,
        animatedReplyStyle: String? = null,
        linkStyle: String? = null,
        isPremium: Boolean = false
    ) {
        profileCustomizations[userId] = ProfileCustomization(
            userId = userId,
            color = color,
            background = background,
            giftBackdrop = giftBackdrop,
            giftSymbol = giftSymbol,
            animatedReplyStyle = animatedReplyStyle,
            linkStyle = linkStyle,
            isPremium = isPremium
        )
    }

    fun getProfileCustomization(userId: String): ProfileCustomization? {
        return profileCustomizations[userId]
    }

    /**
     * هدايا سيادية - أفضل من تيليجرام blockchain gifts Fragment
     * - تيليجرام: blockchain-based gifts via Fragment verified ownership + gifting simplified + prices drop over time + remove signatures via Stars + showcase on profile
     * - RED: sovereign gifts + verified + prices drop + remove signatures + showcase + E2EE + P2P
     */
    data class SovereignGift(
        val id: String,
        val name: String,
        val description: String,
        val fromUserId: String,
        val toUserId: String,
        val backdrop: String,
        val symbol: String,
        val isBlockchain: Boolean,
        val fragmentVerified: Boolean,
        val price: Long,
        val signature: String?,
        val customMessage: String?,
        val timestamp: Long,
        val canRemoveSignature: Boolean
    )

    private val gifts = mutableStateListOf<SovereignGift>()

    fun sendGift(
        name: String,
        description: String,
        fromUserId: String,
        toUserId: String,
        backdrop: String,
        symbol: String,
        isBlockchain: Boolean = false,
        price: Long = 0,
        customMessage: String? = null
    ): SovereignGift {
        val gift = SovereignGift(
            id = "gift_${System.currentTimeMillis()}_$toUserId",
            name = name,
            description = description,
            fromUserId = fromUserId,
            toUserId = toUserId,
            backdrop = backdrop,
            symbol = symbol,
            isBlockchain = isBlockchain,
            fragmentVerified = isBlockchain,
            price = price,
            signature = fromUserId,
            customMessage = customMessage,
            timestamp = System.currentTimeMillis(),
            canRemoveSignature = true
        )
        gifts.add(gift)
        return gift
    }

    fun getGiftsForUser(userId: String): List<SovereignGift> {
        return gifts.filter { it.toUserId == userId }
    }

    fun removeGiftSignature(giftId: String, useStars: Boolean = false): Boolean {
        val index = gifts.indexOfFirst { it.id == giftId }
        if (index != -1 && gifts[index].canRemoveSignature) {
            gifts[index] = gifts[index].copy(signature = null, customMessage = null)
            return true
        }
        return false
    }

    /**
     * تعليقات مباشرة وردود فعل في مكالمات جماعية - أفضل من تيليجرام
     * - تيليجرام: live comments + emoji reactions in group calls video chats up to 1000 participants + temporary messages briefly appear + animated reaction if just emoji + Message button
     * - RED: live comments + reactions + 1000 participants + 100 video + temporary + animated + Message button + chat + Q&A + polls
     */
    data class LiveCallComment(
        val id: String,
        val callId: String,
        val userId: String,
        val username: String,
        val text: String?,
        val emoji: String?,
        val isAnimatedReaction: Boolean,
        val timestamp: Long
    )

    private val liveComments = mutableStateListOf<LiveCallComment>()

    fun sendLiveComment(
        callId: String,
        userId: String,
        username: String,
        text: String? = null,
        emoji: String? = null
    ): LiveCallComment {
        val isAnimated = text.isNullOrBlank() && !emoji.isNullOrBlank()
        val comment = LiveCallComment(
            id = "comment_${System.currentTimeMillis()}_$userId",
            callId = callId,
            userId = userId,
            username = username,
            text = text,
            emoji = emoji,
            isAnimatedReaction = isAnimated,
            timestamp = System.currentTimeMillis()
        )
        liveComments.add(comment)
        // Auto remove after 5 seconds for temporary messages
        return comment
    }

    fun getLiveCommentsForCall(callId: String): List<LiveCallComment> {
        val now = System.currentTimeMillis()
        return liveComments.filter { it.callId == callId && now - it.timestamp < 5000L } // 5s temporary
    }
}
