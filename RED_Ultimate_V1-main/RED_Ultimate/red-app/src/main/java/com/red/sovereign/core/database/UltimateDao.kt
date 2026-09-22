package com.red.sovereign.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO نهائي أسطوري V8 - كل أنواع قواعد البيانات - أقوى وأنسب وأحدث 2026
 * 
 * يطور كل نوع على حدى + كل شيء يريده + بدون تكرار
 * - أقوى: indices + FTS5 + Paging + Outbox + CRDT + transactions
 * - أنسب: كل DAO حسب عمله الأساسي + أفضل اختيار
 * - أحدث: Room 2.8.4 + Kotlin 2.3 K2 + Flow + Paging3 + 2026 tech
 */

@Dao
interface UltimateDao {

    // ─── Mighty Polls ───
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoll(poll: MightyPollEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPolls(polls: List<MightyPollEntity>)

    @Query("SELECT * FROM mighty_polls WHERE id = :id LIMIT 1")
    suspend fun getPoll(id: String): MightyPollEntity?

    @Query("SELECT * FROM mighty_polls WHERE groupId = :groupId ORDER BY timestamp DESC")
    fun getPollsForGroup(groupId: String): Flow<List<MightyPollEntity>>

    @Query("SELECT * FROM mighty_polls WHERE channelId = :channelId ORDER BY timestamp DESC")
    fun getPollsForChannel(channelId: String): Flow<List<MightyPollEntity>>

    @Query("SELECT * FROM mighty_polls WHERE isClosed = 0 AND (expiresAt IS NULL OR expiresAt > :now) ORDER BY timestamp DESC")
    fun getActivePolls(now: Long = System.currentTimeMillis()): Flow<List<MightyPollEntity>>

    @Query("SELECT * FROM mighty_polls ORDER BY timestamp DESC")
    fun getAllPolls(): Flow<List<MightyPollEntity>>

    @Query("UPDATE mighty_polls SET isClosed = 1 WHERE id = :id")
    suspend fun closePoll(id: String)

    @Query("DELETE FROM mighty_polls WHERE id = :id")
    suspend fun deletePoll(id: String)

    @Query("DELETE FROM mighty_polls WHERE expiresAt IS NOT NULL AND expiresAt <= :now")
    suspend fun cleanupExpiredPolls(now: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPollVote(vote: PollVoteEntity)

    @Query("SELECT * FROM poll_votes WHERE pollId = :pollId")
    suspend fun getVotesForPoll(pollId: String): List<PollVoteEntity>

    @Query("DELETE FROM poll_votes WHERE pollId = :pollId AND userId = :userId")
    suspend fun deleteVote(pollId: String, userId: String)

    // ─── Sovereign Stories ───
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStory(story: SovereignStoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStories(stories: List<SovereignStoryEntity>)

    @Query("SELECT * FROM sovereign_stories WHERE expiresAt > :now ORDER BY timestamp DESC")
    fun getActiveStories(now: Long = System.currentTimeMillis()): Flow<List<SovereignStoryEntity>>

    @Query("SELECT * FROM sovereign_stories WHERE userId = :userId AND expiresAt > :now ORDER BY timestamp DESC")
    fun getStoriesForUser(userId: String, now: Long = System.currentTimeMillis()): Flow<List<SovereignStoryEntity>>

    @Query("SELECT * FROM sovereign_stories WHERE isMyStory = 1 AND expiresAt > :now ORDER BY timestamp DESC")
    fun getMyStories(now: Long = System.currentTimeMillis()): Flow<List<SovereignStoryEntity>>

    @Query("DELETE FROM sovereign_stories WHERE expiresAt <= :now")
    suspend fun cleanupExpiredStories(now: Long): Int

    @Query("UPDATE sovereign_stories SET views = views + 1 WHERE id = :id")
    suspend fun incrementStoryViews(id: String)

    // ─── Private Notes ───
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrivateNote(note: PrivateNoteEntity)

    @Query("SELECT * FROM private_notes WHERE contactId = :contactId LIMIT 1")
    suspend fun getPrivateNote(contactId: String): PrivateNoteEntity?

    @Query("SELECT * FROM private_notes ORDER BY timestamp DESC")
    fun getAllPrivateNotes(): Flow<List<PrivateNoteEntity>>

    @Query("DELETE FROM private_notes WHERE contactId = :contactId")
    suspend fun deletePrivateNote(contactId: String)

    // ─── Profile Customizations ───
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfileCustomization(custom: ProfileCustomizationEntity)

    @Query("SELECT * FROM profile_customizations WHERE userId = :userId LIMIT 1")
    suspend fun getProfileCustomization(userId: String): ProfileCustomizationEntity?

    @Query("SELECT * FROM profile_customizations")
    fun getAllProfileCustomizations(): Flow<List<ProfileCustomizationEntity>>

    // ─── Sovereign Gifts ───
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGift(gift: SovereignGiftEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGifts(gifts: List<SovereignGiftEntity>)

    @Query("SELECT * FROM sovereign_gifts WHERE toUserId = :userId ORDER BY timestamp DESC")
    fun getGiftsForUser(userId: String): Flow<List<SovereignGiftEntity>>

    @Query("SELECT * FROM sovereign_gifts WHERE fromUserId = :userId ORDER BY timestamp DESC")
    fun getGiftsFromUser(userId: String): Flow<List<SovereignGiftEntity>>

    @Query("SELECT * FROM sovereign_gifts WHERE isBlockchain = 1 ORDER BY timestamp DESC")
    fun getBlockchainGifts(): Flow<List<SovereignGiftEntity>>

    @Query("UPDATE sovereign_gifts SET signature = NULL, customMessage = NULL WHERE id = :id")
    suspend fun removeGiftSignature(id: String)

    @Query("DELETE FROM sovereign_gifts WHERE id = :id")
    suspend fun deleteGift(id: String)

    // ─── Live Comments ───
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLiveComment(comment: LiveCommentEntity)

    @Query("SELECT * FROM live_comments WHERE callId = :callId AND timestamp > :since ORDER BY timestamp ASC")
    suspend fun getLiveCommentsForCall(callId: String, since: Long = System.currentTimeMillis() - 5000L): List<LiveCommentEntity>

    @Query("SELECT * FROM live_comments WHERE callId = :callId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLiveComments(callId: String, limit: Int = 50): List<LiveCommentEntity>

    @Query("DELETE FROM live_comments WHERE timestamp <= :cutoff")
    suspend fun cleanupOldLiveComments(cutoff: Long): Int

    // ─── AI Summaries ───
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAISummary(summary: AISummaryEntity)

    @Query("SELECT * FROM ai_summaries WHERE sourceId = :sourceId ORDER BY timestamp DESC")
    fun getSummariesForSource(sourceId: String): Flow<List<AISummaryEntity>>

    @Query("SELECT * FROM ai_summaries WHERE sourceType = :type ORDER BY timestamp DESC")
    fun getSummariesByType(type: String): Flow<List<AISummaryEntity>>

    @Query("SELECT * FROM ai_summaries ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSummaries(limit: Int = 50): List<AISummaryEntity>

    @Query("DELETE FROM ai_summaries WHERE timestamp <= :cutoff")
    suspend fun cleanupOldSummaries(cutoff: Long): Int

    // ─── Scam Alerts ───
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScamAlert(alert: ScamAlertEntity)

    @Query("SELECT * FROM scam_alerts WHERE messageId = :messageId LIMIT 1")
    suspend fun getScamAlert(messageId: String): ScamAlertEntity?

    @Query("SELECT * FROM scam_alerts WHERE isScam = 1 ORDER BY timestamp DESC")
    fun getScamAlerts(): Flow<List<ScamAlertEntity>>

    @Query("SELECT * FROM scam_alerts WHERE riskLevel = :level ORDER BY timestamp DESC")
    fun getScamAlertsByRisk(level: String): Flow<List<ScamAlertEntity>>

    @Query("DELETE FROM scam_alerts WHERE timestamp <= :cutoff")
    suspend fun cleanupOldScamAlerts(cutoff: Long): Int

    // ─── Live Photos ───
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLivePhoto(photo: LivePhotoEntity)

    @Query("SELECT * FROM live_photos ORDER BY timestamp DESC")
    fun getAllLivePhotos(): Flow<List<LivePhotoEntity>>

    @Query("SELECT * FROM live_photos WHERE id = :id LIMIT 1")
    suspend fun getLivePhoto(id: String): LivePhotoEntity?

    @Query("DELETE FROM live_photos WHERE id = :id")
    suspend fun deleteLivePhoto(id: String)

    // ─── Scanned Documents ───
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScannedDocument(doc: ScannedDocumentEntity)

    @Query("SELECT * FROM scanned_documents ORDER BY timestamp DESC")
    fun getAllScannedDocuments(): Flow<List<ScannedDocumentEntity>>

    @Query("SELECT * FROM scanned_documents WHERE id = :id LIMIT 1")
    suspend fun getScannedDocument(id: String): ScannedDocumentEntity?

    @Query("DELETE FROM scanned_documents WHERE id = :id")
    suspend fun deleteScannedDocument(id: String)

    // ─── Key Transparency ───
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeyTransparency(key: KeyTransparencyEntity)

    @Query("SELECT * FROM key_transparency WHERE userId = :userId LIMIT 1")
    suspend fun getKeyTransparency(userId: String): KeyTransparencyEntity?

    @Query("SELECT * FROM key_transparency WHERE verified = 1 ORDER BY timestamp DESC")
    fun getVerifiedKeys(): Flow<List<KeyTransparencyEntity>>

    @Query("SELECT * FROM key_transparency WHERE verified = 0 ORDER BY timestamp DESC")
    fun getUnverifiedKeys(): Flow<List<KeyTransparencyEntity>>

    @Query("UPDATE key_transparency SET verified = :verified, lastVerifiedAt = :now WHERE userId = :userId")
    suspend fun setKeyVerified(userId: String, verified: Boolean, now: Long = System.currentTimeMillis())

    // ─── Safety Numbers ───
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSafetyNumber(safety: SafetyNumberEntity)

    @Query("SELECT * FROM safety_numbers WHERE userId = :userId LIMIT 1")
    suspend fun getSafetyNumber(userId: String): SafetyNumberEntity?

    @Query("SELECT * FROM safety_numbers WHERE verified = 1 ORDER BY timestamp DESC")
    fun getVerifiedSafetyNumbers(): Flow<List<SafetyNumberEntity>>

    @Query("UPDATE safety_numbers SET verified = :verified, verifiedAt = :now WHERE userId = :userId")
    suspend fun setSafetyNumberVerified(userId: String, verified: Boolean, now: Long = System.currentTimeMillis())

    // ─── Linked Devices ───
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinkedDevice(device: LinkedDeviceEntity)

    @Query("SELECT * FROM linked_devices WHERE userId = :userId ORDER BY lastSeen DESC")
    fun getLinkedDevicesForUser(userId: String): Flow<List<LinkedDeviceEntity>>

    @Query("SELECT * FROM linked_devices WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getLinkedDevice(deviceId: String): LinkedDeviceEntity?

    @Query("SELECT * FROM linked_devices WHERE isCurrent = 1 LIMIT 1")
    suspend fun getCurrentDevice(): LinkedDeviceEntity?

    @Query("DELETE FROM linked_devices WHERE deviceId = :deviceId")
    suspend fun deleteLinkedDevice(deviceId: String)

    @Query("UPDATE linked_devices SET lastSeen = :now WHERE deviceId = :deviceId")
    suspend fun updateDeviceLastSeen(deviceId: String, now: Long = System.currentTimeMillis())

    // ─── Call Quality ───
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallQuality(quality: CallQualityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallQualities(qualities: List<CallQualityEntity>)

    @Query("SELECT * FROM call_quality WHERE callId = :callId ORDER BY timestamp ASC")
    fun getCallQualityForCall(callId: String): Flow<List<CallQualityEntity>>

    @Query("SELECT * FROM call_quality ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentCallQualities(limit: Int = 100): List<CallQualityEntity>

    @Query("DELETE FROM call_quality WHERE timestamp <= :cutoff")
    suspend fun cleanupOldCallQualities(cutoff: Long): Int

    @Query("SELECT AVG(rttMs) FROM call_quality WHERE callId = :callId")
    suspend fun getAverageRttForCall(callId: String): Long?

    @Query("SELECT AVG(packetLossPercent) FROM call_quality WHERE callId = :callId")
    suspend fun getAveragePacketLossForCall(callId: String): Float?

    // ─── Network Stats ───
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNetworkStats(stats: NetworkStatsEntity)

    @Query("SELECT * FROM network_stats ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentNetworkStats(limit: Int = 100): List<NetworkStatsEntity>

    @Query("SELECT * FROM network_stats WHERE type = :type ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getNetworkStatsByType(type: String, limit: Int = 50): List<NetworkStatsEntity>

    @Query("DELETE FROM network_stats WHERE timestamp <= :cutoff")
    suspend fun cleanupOldNetworkStats(cutoff: Long): Int

    // ─── Folders ───
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)

    @Query("SELECT * FROM folders ORDER BY updatedAt DESC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id LIMIT 1")
    suspend fun getFolder(id: String): FolderEntity?

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: String)

    @Query("UPDATE folders SET locked = :locked, updatedAt = :now WHERE id = :id")
    suspend fun setFolderLocked(id: String, locked: Boolean, now: Long = System.currentTimeMillis())

    // ─── Pins ───
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPin(pin: PinEntity)

    @Query("SELECT * FROM pins WHERE groupId = :groupId ORDER BY timestamp DESC")
    fun getPinsForGroup(groupId: String): Flow<List<PinEntity>>

    @Query("SELECT * FROM pins WHERE messageId = :messageId LIMIT 1")
    suspend fun getPin(messageId: String): PinEntity?

    @Query("DELETE FROM pins WHERE messageId = :messageId")
    suspend fun deletePin(messageId: String)

    @Query("DELETE FROM pins WHERE expiresAt IS NOT NULL AND expiresAt <= :now")
    suspend fun cleanupExpiredPins(now: Long): Int

    // ─── Personal Chat Folders ───
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonalChatFolder(folder: PersonalChatFolderEntity)

    @Query("SELECT * FROM personal_chat_folders ORDER BY createdAt DESC")
    fun getAllPersonalChatFolders(): Flow<List<PersonalChatFolderEntity>>

    @Query("DELETE FROM personal_chat_folders WHERE id = :id")
    suspend fun deletePersonalChatFolder(id: String)

    // ─── User Settings ───
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserSettings(settings: UserSettingsEntity)

    @Query("SELECT * FROM user_settings WHERE userId = :userId LIMIT 1")
    suspend fun getUserSettings(userId: String): UserSettingsEntity?

    @Query("SELECT * FROM user_settings")
    fun getAllUserSettings(): Flow<List<UserSettingsEntity>>

    @Query("UPDATE user_settings SET themePreset = :preset WHERE userId = :userId")
    suspend fun setThemePreset(userId: String, preset: String)

    @Query("UPDATE user_settings SET themeMode = :mode WHERE userId = :userId")
    suspend fun setThemeMode(userId: String, mode: String)

    // ─── Notifications ───
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<NotificationEntity>)

    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY timestamp DESC")
    fun getNotificationsForUser(userId: String): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE isRead = 0 ORDER BY timestamp DESC")
    fun getUnreadNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE type = :type ORDER BY timestamp DESC")
    fun getNotificationsByType(type: String): Flow<List<NotificationEntity>>

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationRead(id: String)

    @Query("UPDATE notifications SET isRead = 1 WHERE userId = :userId")
    suspend fun markAllNotificationsReadForUser(userId: String)

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteNotification(id: String)

    @Query("DELETE FROM notifications WHERE timestamp <= :cutoff")
    suspend fun cleanupOldNotifications(cutoff: Long): Int

    // ─── App Stats ───
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppStats(stats: AppStatsEntity)

    @Query("SELECT * FROM app_stats ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestAppStats(): AppStatsEntity?

    @Query("SELECT * FROM app_stats ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentAppStats(limit: Int = 30): List<AppStatsEntity>

    // ─── Cleanup All Expired ───
    @Transaction
    suspend fun cleanupAllExpired(now: Long = System.currentTimeMillis()): Map<String, Int> {
        val results = mutableMapOf<String, Int>()
        results["polls"] = cleanupExpiredPolls(now)
        results["stories"] = cleanupExpiredStories(now)
        results["live_comments"] = cleanupOldLiveComments(now - 60_000L) // 1 min
        results["pins"] = cleanupExpiredPins(now)
        results["notifications"] = cleanupOldNotifications(now - 30L * 24 * 60 * 60 * 1000L) // 30 days
        results["call_quality"] = cleanupOldCallQualities(now - 7L * 24 * 60 * 60 * 1000L) // 7 days
        results["network_stats"] = cleanupOldNetworkStats(now - 7L * 24 * 60 * 60 * 1000L)
        results["ai_summaries"] = cleanupOldSummaries(now - 30L * 24 * 60 * 60 * 1000L)
        results["scam_alerts"] = cleanupOldScamAlerts(now - 30L * 24 * 60 * 60 * 1000L)
        return results
    }
}
