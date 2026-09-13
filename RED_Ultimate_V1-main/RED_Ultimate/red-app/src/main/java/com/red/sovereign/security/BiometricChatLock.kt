package com.red.sovereign.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.red.sovereign.core.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 👁️ BiometricChatLock — قفل المحادثات الفردية والجماعية ببصمة الأصبع/الوجه
 *
 * يتيح للمستخدم قفل أي محادثة محددة بشكل فردي وحمايتها بالبصمة الحيوية
 * أو رمز قفل الشاشة، مع إخفائها من القائمة حتى يتم إلغاء قفلها.
 */
object BiometricChatLock {

    private const val LOCKED_CHATS_KEY = "sovereign_locked_chat_ids"
    private const val LOCK_STORE_NAME = "chat_lock"
    private val unlockedInSession = mutableSetOf<String>()

    private val _lockedChatsCount = MutableStateFlow(0)
    val lockedChatsCount = _lockedChatsCount.asStateFlow()

    fun isBiometricAvailable(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun isChatLocked(context: Context, conversationId: String): Boolean {
        val lockedSet = getLockedChatIds(context)
        return conversationId in lockedSet
    }

    fun isChatUnlockedInSession(conversationId: String): Boolean {
        return conversationId in unlockedInSession
    }

    fun lockChat(context: Context, conversationId: String) {
        val lockedSet = getLockedChatIds(context).toMutableSet()
        lockedSet.add(conversationId)
        saveLockedChatIds(context, lockedSet)
        unlockedInSession.remove(conversationId)
    }

    fun unlockChat(context: Context, conversationId: String) {
        val lockedSet = getLockedChatIds(context).toMutableSet()
        lockedSet.remove(conversationId)
        saveLockedChatIds(context, lockedSet)
        unlockedInSession.remove(conversationId)
    }

    fun authenticateAndOpenChat(
        activity: FragmentActivity,
        conversationId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isChatLocked(activity, conversationId) || isChatUnlockedInSession(conversationId)) {
            onSuccess()
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                unlockedInSession.add(conversationId)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("فشلت المحاولة — حاول مجدداً")
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("فتح المحادثة المقفولة")
            .setSubtitle("تأكيد الهوية عبر بصمة الأصبع أو رمز الجهاز")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun getLockedChatIds(context: Context): Set<String> {
        val store = SecureStore(context, LOCK_STORE_NAME)
        val raw = store.get(LOCKED_CHATS_KEY) ?: ""
        if (raw.isBlank()) return emptySet()
        val set = raw.split(",").filter { it.isNotBlank() }.toSet()
        _lockedChatsCount.value = set.size
        return set
    }

    private fun saveLockedChatIds(context: Context, ids: Set<String>) {
        val store = SecureStore(context, LOCK_STORE_NAME)
        val joined = ids.joinToString(",")
        store.put(LOCKED_CHATS_KEY, joined)
        _lockedChatsCount.value = ids.size
    }
}
