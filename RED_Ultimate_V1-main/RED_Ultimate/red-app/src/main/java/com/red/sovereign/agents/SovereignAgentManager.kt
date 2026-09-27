package com.red.sovereign.agents

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * مدير نظام الـ AI Agents السيادي لتطبيق RED.
 *
 * يوفر:
 * - تسجيل وإدارة الـ 20 Agent متخصص
 * - توجيه الأحداث للـ Agents المناسبة
 * - قراءة الحالة من الـ ViewModel بشكل reactive
 * - حوكمة الأذونات (من يمكنه تشغيل أي Agent)
 *
 * الاستخدام:
 * ```kotlin
 * val manager = SovereignAgentManager()
 * manager.dispatch(AgentContext(groupId = "g1", senderId = "u1", messageContent = "hello"))
 * ```
 */
class SovereignAgentManager : ViewModel() {

    private val orchestrator = AgentOrchestrator()

    private val _lastResults = MutableStateFlow<List<Pair<String, AgentResult>>>(emptyList())
    val lastResults: StateFlow<List<Pair<String, AgentResult>>> = _lastResults.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    val agentActivity: StateFlow<Map<String, String>> = orchestrator.agentActivityState

    init {
        // تسجيل كل الـ Agents
        registerAllAgents()
    }

    /**
     * تسجيل جميع الـ Agents المتخصصة — من [RedAgentRegistry] مصدراً واحداً.
     * (كانت القائمة مكتوبة هنا نسخة ثانية من أسماء لم تُعرَّف أصلاً، فيفشل البناء.)
     */
    private fun registerAllAgents() {
        RedAgentRegistry.getAllAgents().forEach(orchestrator::registerAgent)
    }

    /**
     * إرسال حدث لجميع الـ Agents النشطة.
     *
     * @param context السياق الكامل للحدث (المجموعة، المرسل، المحتوى)
     */
    fun dispatch(context: AgentContext) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val results = withContext(Dispatchers.Default) {
                    orchestrator.dispatchEvent(context)
                }
                _lastResults.value = results
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * إرسال حدث رسالة واحدة — helper مبسّط.
     */
    fun onMessageReceived(groupId: String, senderId: String, text: String) {
        dispatch(AgentContext(groupId = groupId, senderId = senderId, messageContent = text))
    }

    /**
     * فلترة النتائج للحصول على الأحداث المُعلَّمة فقط (Flagged).
     */
    fun getFlaggedResults(): List<Pair<String, AgentResult.Flagged>> =
        _lastResults.value
            .filter { it.second is AgentResult.Flagged }
            .map { it.first to it.second as AgentResult.Flagged }

    /**
     * فلترة النتائج للحصول على الردود الناجحة فقط (Success).
     */
    fun getSuccessResponses(): List<Pair<String, AgentResult.Success>> =
        _lastResults.value
            .filter { it.second is AgentResult.Success }
            .map { it.first to it.second as AgentResult.Success }
}