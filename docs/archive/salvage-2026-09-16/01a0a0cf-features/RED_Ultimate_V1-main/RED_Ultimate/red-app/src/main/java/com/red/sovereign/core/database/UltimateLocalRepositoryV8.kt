package com.red.sovereign.core.database

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * ULTIMATE LOCAL REPOSITORY V8 - أسطوري كامل - أقوى وأنسب وأحدث 2026
 * 
 * يطور كل أنواع قواعد البيانات على حدى + يضيف كل شيء يريده
 * - 12 نوع قديم محسن (Message, LocalHistory, Conversation, Contact, Group, CallLog, Story, Draft, Reaction, Outbox, Starred, MediaUpload)
 * - 21 نوع جديد 2026 (Poll, PollVote, SovereignStory, PrivateNote, ProfileCustomization, Gift, LiveComment, AISummary, ScamAlert, LivePhoto, ScannedDoc, KeyTransparency, SafetyNumber, LinkedDevice, CallQuality, NetworkStats, Folder, Pin, PersonalFolder, UserSettings, Notification, AppStats)
 * 
 * أقوى: SQLCipher 256-bit AES HMAC + indices مركبة + FTS5 + Paging3 + Outbox + CRDT
 * أنسب: كل repository حسب عمله الأساسي - Polls للمجموعات، Stories للقصص، Gifts للهدايا، etc
 * أحدث: Room 2.8.4 Kotlin 2.3 K2 Flow 2026 - نفس تطور WhatsApp/Telegram/Discord/Signal
 */

class UltimateLocalRepositoryV8(context: Context) {
    private val appCtx = context.applicationContext
    private val db = RedDatabase.getInstance(context)
    private val dao = db.redDao()
    private val ultimateDao = db.ultimateDao()
    private val outboxDao = db.outboxDao()
    private val mediaUploadDao = db.mediaUploadDao()

    // ─── Legacy delegates (V7 compatible) ───
    val legacy = LocalRepository(context)

    // ─── Mighty Polls - أفضل من واتساب وتيليجرام - 20+ ميزة ───
    suspend fun savePoll(poll: MightyPollEntity) = ultimateDao.insertPoll(poll)
    suspend fun savePolls(polls: List<MightyPollEntity>) = ultimateDao.insertPolls(polls)
    suspend fun getPoll(id: String) = ultimateDao.getPoll(id)
    fun getPollsForGroup(groupId: String) = ultimateDao.getPollsForGroup(groupId)
    fun getPollsForChannel(channelId: String) = ultimateDao.getPollsForChannel(channelId)
    fun getActivePolls() = ultimateDao.getActivePolls()
    fun getAllPolls() = ultimateDao.getAllPolls()
    suspend fun closePoll(id: String) = ultimateDao.closePoll(id)
    suspend fun deletePoll(id: String) = ultimateDao.deletePoll(id)

    suspend fun votePoll(pollId: String, userId: String, optionIndex: Int) {
        ultimateDao.insertPollVote(PollVoteEntity(pollId, userId, optionIndex, System.currentTimeMillis()))
    }
    suspend fun getVotesForPoll(pollId: String) = ultimateDao.getVotesForPoll(pollId)
    suspend fun unvotePoll(pollId: String, userId: String) = ultimateDao.deleteVote(pollId, userId)

    // ─── Sovereign Stories - أفضل من واتساب Status + تيليجرام Stories - 24h premium Live/Loop/Bounce ───
    suspend fun saveSovereignStory(story: SovereignStoryEntity) = ultimateDao.insertStory(story)
    suspend fun saveSovereignStories(stories: List<SovereignStoryEntity>) = ultimateDao.insertStories(stories)
    fun getActiveSovereignStories() = ultimateDao.getActiveStories()
    fun getSovereignStoriesForUser(userId: String) = ultimateDao.getStoriesForUser(userId)
    fun getMySovereignStories() = ultimateDao.getMyStories()
    suspend fun incrementStoryViews(id: String) = ultimateDao.incrementStoryViews(id)

    // ─── Private Notes - مثل تيليجرام - E2EE SQLCipher ───
    suspend fun savePrivateNote(note: PrivateNoteEntity) = ultimateDao.insertPrivateNote(note)
    suspend fun getPrivateNote(contactId: String) = ultimateDao.getPrivateNote(contactId)
    fun getAllPrivateNotes() = ultimateDao.getAllPrivateNotes()
    suspend fun deletePrivateNote(contactId: String) = ultimateDao.deletePrivateNote(contactId)

    // ─── Profile Customizations - مثل تيليجرام premium colors ───
    suspend fun saveProfileCustomization(custom: ProfileCustomizationEntity) = ultimateDao.insertProfileCustomization(custom)
    suspend fun getProfileCustomization(userId: String) = ultimateDao.getProfileCustomization(userId)
    fun getAllProfileCustomizations() = ultimateDao.getAllProfileCustomizations()

    // ─── Sovereign Gifts - blockchain Fragment Stars - أفضل من تيليجرام ───
    suspend fun saveGift(gift: SovereignGiftEntity) = ultimateDao.insertGift(gift)
    suspend fun saveGifts(gifts: List<SovereignGiftEntity>) = ultimateDao.insertGifts(gifts)
    fun getGiftsForUser(userId: String) = ultimateDao.getGiftsForUser(userId)
    fun getGiftsFromUser(userId: String) = ultimateDao.getGiftsFromUser(userId)
    fun getBlockchainGifts() = ultimateDao.getBlockchainGifts()
    suspend fun removeGiftSignature(id: String) = ultimateDao.removeGiftSignature(id)
    suspend fun deleteGift(id: String) = ultimateDao.deleteGift(id)

    // ─── Live Comments - 5s temp animated - مثل تيليجرام live comments ───
    suspend fun saveLiveComment(comment: LiveCommentEntity) = ultimateDao.insertLiveComment(comment)
    suspend fun getLiveCommentsForCall(callId: String) = ultimateDao.getLiveCommentsForCall(callId)
    suspend fun getRecentLiveComments(callId: String, limit: Int = 50) = ultimateDao.getRecentLiveComments(callId, limit)

    // ─── AI Summaries - Cocoon encrypted - أفضل من واتساب Meta AI + تيليجرام Smart Summaries ───
    suspend fun saveAISummary(summary: AISummaryEntity) = ultimateDao.insertAISummary(summary)
    fun getSummariesForSource(sourceId: String) = ultimateDao.getSummariesForSource(sourceId)
    fun getSummariesByType(type: String) = ultimateDao.getSummariesByType(type)
    suspend fun getRecentSummaries(limit: Int = 50) = ultimateDao.getRecentSummaries(limit)

    // ─── Scam Alerts - on-device anti-phishing - مثل Signal + واتساب Scam Alert ───
    suspend fun saveScamAlert(alert: ScamAlertEntity) = ultimateDao.insertScamAlert(alert)
    suspend fun getScamAlert(messageId: String) = ultimateDao.getScamAlert(messageId)
    fun getScamAlerts() = ultimateDao.getScamAlerts()
    fun getScamAlertsByRisk(level: String) = ultimateDao.getScamAlertsByRisk(level)

    // ─── Live Photos - Motion Photos Live/Loop/Bounce ───
    suspend fun saveLivePhoto(photo: LivePhotoEntity) = ultimateDao.insertLivePhoto(photo)
    fun getAllLivePhotos() = ultimateDao.getAllLivePhotos()
    suspend fun getLivePhoto(id: String) = ultimateDao.getLivePhoto(id)
    suspend fun deleteLivePhoto(id: String) = ultimateDao.deleteLivePhoto(id)

    // ─── Scanned Documents - OCR FTS5 E2EE ───
    suspend fun saveScannedDocument(doc: ScannedDocumentEntity) = ultimateDao.insertScannedDocument(doc)
    fun getAllScannedDocuments() = ultimateDao.getAllScannedDocuments()
    suspend fun getScannedDocument(id: String) = ultimateDao.getScannedDocument(id)
    suspend fun deleteScannedDocument(id: String) = ultimateDao.deleteScannedDocument(id)

    // ─── Key Transparency - Cloudflare TrailOfBits green checkmark - مثل Signal ───
    suspend fun saveKeyTransparency(key: KeyTransparencyEntity) = ultimateDao.insertKeyTransparency(key)
    suspend fun getKeyTransparency(userId: String) = ultimateDao.getKeyTransparency(userId)
    fun getVerifiedKeys() = ultimateDao.getVerifiedKeys()
    fun getUnverifiedKeys() = ultimateDao.getUnverifiedKeys()
    suspend fun setKeyVerified(userId: String, verified: Boolean) = ultimateDao.setKeyVerified(userId, verified)

    // ─── Safety Numbers - QR verification ───
    suspend fun saveSafetyNumber(safety: SafetyNumberEntity) = ultimateDao.insertSafetyNumber(safety)
    suspend fun getSafetyNumber(userId: String) = ultimateDao.getSafetyNumber(userId)
    fun getVerifiedSafetyNumbers() = ultimateDao.getVerifiedSafetyNumbers()
    suspend fun setSafetyNumberVerified(userId: String, verified: Boolean) = ultimateDao.setSafetyNumberVerified(userId, verified)

    // ─── Linked Devices - multi-device no phone online - أفضل من واتساب + Signal ───
    suspend fun saveLinkedDevice(device: LinkedDeviceEntity) = ultimateDao.insertLinkedDevice(device)
    fun getLinkedDevicesForUser(userId: String) = ultimateDao.getLinkedDevicesForUser(userId)
    suspend fun getLinkedDevice(deviceId: String) = ultimateDao.getLinkedDevice(deviceId)
    suspend fun getCurrentDevice() = ultimateDao.getCurrentDevice()
    suspend fun deleteLinkedDevice(deviceId: String) = ultimateDao.deleteLinkedDevice(deviceId)
    suspend fun updateDeviceLastSeen(deviceId: String) = ultimateDao.updateDeviceLastSeen(deviceId)

    // ─── Call Quality - AV1 Opus RTT packetLoss - مثل LiveKit + WebRTC 2026 ───
    suspend fun saveCallQuality(quality: CallQualityEntity) = ultimateDao.insertCallQuality(quality)
    suspend fun saveCallQualities(qualities: List<CallQualityEntity>) = ultimateDao.insertCallQualities(qualities)
    fun getCallQualityForCall(callId: String) = ultimateDao.getCallQualityForCall(callId)
    suspend fun getRecentCallQualities(limit: Int = 100) = ultimateDao.getRecentCallQualities(limit)
    suspend fun getAverageRttForCall(callId: String) = ultimateDao.getAverageRttForCall(callId)
    suspend fun getAveragePacketLossForCall(callId: String) = ultimateDao.getAveragePacketLossForCall(callId)

    // ─── Network Stats - LAN detection - P2P Yemen networks ───
    suspend fun saveNetworkStats(stats: NetworkStatsEntity) = ultimateDao.insertNetworkStats(stats)
    suspend fun getRecentNetworkStats(limit: Int = 100) = ultimateDao.getRecentNetworkStats(limit)
    suspend fun getNetworkStatsByType(type: String, limit: Int = 50) = ultimateDao.getNetworkStatsByType(type, limit)

    // ─── Folders - مثل تيليجرام personal chat folders ───
    suspend fun saveFolder(folder: FolderEntity) = ultimateDao.insertFolder(folder)
    fun getAllFolders() = ultimateDao.getAllFolders()
    suspend fun getFolder(id: String) = ultimateDao.getFolder(id)
    suspend fun deleteFolder(id: String) = ultimateDao.deleteFolder(id)
    suspend fun setFolderLocked(id: String, locked: Boolean) = ultimateDao.setFolderLocked(id, locked)

    // ─── Pins - expires 7d - مثل تيليجرام + Discord ───
    suspend fun savePin(pin: PinEntity) = ultimateDao.insertPin(pin)
    fun getPinsForGroup(groupId: String) = ultimateDao.getPinsForGroup(groupId)
    suspend fun getPin(messageId: String) = ultimateDao.getPin(messageId)
    suspend fun deletePin(messageId: String) = ultimateDao.deletePin(messageId)

    // ─── Personal Chat Folders ───
    suspend fun savePersonalChatFolder(folder: PersonalChatFolderEntity) = ultimateDao.insertPersonalChatFolder(folder)
    fun getAllPersonalChatFolders() = ultimateDao.getAllPersonalChatFolders()
    suspend fun deletePersonalChatFolder(id: String) = ultimateDao.deletePersonalChatFolder(id)

    // ─── User Settings - AAA 7:1 LiquidGlass Haze Mesh - أفضل من Telegram 4-tab ───
    suspend fun saveUserSettings(settings: UserSettingsEntity) = ultimateDao.insertUserSettings(settings)
    suspend fun getUserSettings(userId: String) = ultimateDao.getUserSettings(userId)
    fun getAllUserSettings() = ultimateDao.getAllUserSettings()
    suspend fun setThemePreset(userId: String, preset: String) = ultimateDao.setThemePreset(userId, preset)
    suspend fun setThemeMode(userId: String, mode: String) = ultimateDao.setThemeMode(userId, mode)

    // ─── Notifications ───
    suspend fun saveNotification(notification: NotificationEntity) = ultimateDao.insertNotification(notification)
    suspend fun saveNotifications(notifications: List<NotificationEntity>) = ultimateDao.insertNotifications(notifications)
    fun getNotificationsForUser(userId: String) = ultimateDao.getNotificationsForUser(userId)
    fun getUnreadNotifications() = ultimateDao.getUnreadNotifications()
    fun getNotificationsByType(type: String) = ultimateDao.getNotificationsByType(type)
    suspend fun markNotificationRead(id: String) = ultimateDao.markNotificationRead(id)
    suspend fun markAllNotificationsReadForUser(userId: String) = ultimateDao.markAllNotificationsReadForUser(userId)
    suspend fun deleteNotification(id: String) = ultimateDao.deleteNotification(id)

    // ─── App Stats ───
    suspend fun saveAppStats(stats: AppStatsEntity) = ultimateDao.insertAppStats(stats)
    suspend fun getLatestAppStats() = ultimateDao.getLatestAppStats()
    suspend fun getRecentAppStats(limit: Int = 30) = ultimateDao.getRecentAppStats(limit)

    // ─── Cleanup - كل شيء منتهي - automatic ───
    suspend fun cleanupAllExpired(): Map<String, Int> = ultimateDao.cleanupAllExpired()

    // ─── Outbox + Media Upload - delegates ───
    suspend fun saveOutboxMessage(msg: OutboxMessageEntity) = outboxDao.insert(msg)
    fun getPendingOutbox() = outboxDao.getPending(System.currentTimeMillis())
    suspend fun markOutboxSent(id: String) = outboxDao.markSent(id)
    suspend fun markOutboxFailed(id: String, error: String, nextAttempt: Long) = outboxDao.markFailed(id, error, nextAttempt)

    suspend fun saveMediaUpload(upload: MediaUploadEntity) = mediaUploadDao.insert(upload)
    fun getPendingMediaUploads() = mediaUploadDao.getPending(System.currentTimeMillis())
    suspend fun markMediaUploadSent(messageId: String, url: String, objectKey: String) = mediaUploadDao.markSent(messageId, url, objectKey)
}
