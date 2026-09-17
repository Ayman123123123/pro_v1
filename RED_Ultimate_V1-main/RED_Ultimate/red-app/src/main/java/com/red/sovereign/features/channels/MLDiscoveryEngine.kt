package com.red.sovereign.features.channels

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

/**
 * On-device ML Discovery Engine using TensorFlow Lite
 * All inference runs locally — zero network calls, zero data collection
 */
class MLDiscoveryEngine private constructor(private val context: Context) {

    companion object {
        private const val TAG = "MLDiscoveryEngine"
        private const val MODEL_FILENAME = "channel_recommender.tflite"
        private const val EMBEDDING_DIM = 64
        private var INSTANCE: MLDiscoveryEngine? = null

        fun getInstance(context: Context): MLDiscoveryEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MLDiscoveryEngine(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun destroy() {
            INSTANCE = null
        }
    }

    private var interpreter: org.tensorflow.lite.Interpreter? = null
    private var isModelLoaded = false
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            copyModelIfNeeded()
            val options = org.tensorflow.lite.Interpreter.Options()
                .setNumThreads(4)
                .setUseNNAPI(true)
            val modelFile = File(context.filesDir, MODEL_FILENAME)
            interpreter = org.tensorflow.lite.Interpreter(modelFile, options)
            isModelLoaded = true
            Log.d(TAG, "TensorFlow Lite model loaded successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ML model", e)
            false
        }
    }

    private fun copyModelIfNeeded() {
        val modelFile = File(context.filesDir, MODEL_FILENAME)
        if (!modelFile.exists()) {
            context.assets.open(MODEL_FILENAME).use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    suspend fun generateRecommendations(
        userEmbedding: UserEmbedding,
        candidateChannels: List<Channel>,
        userPreferences: UserPreferences,
        limit: Int = 20
    ): List<ChannelRecommendation> = withContext(Dispatchers.IO) {
        if (!isModelLoaded || interpreter == null) {
            return@withContext fallbackRecommendations(candidateChannels, userPreferences, limit)
        }

        val candidates = candidateChannels.take(100) // Limit candidates for performance
        val recommendations = mutableListOf<ChannelRecommendation>()

        for (channel in candidates) {
            val channelEmbedding = getOrCreateChannelEmbedding(channel)
            val score = computeSimilarity(userEmbedding.vector, channelEmbedding.vector)
            val reason = determineReason(userEmbedding, channelEmbedding, userPreferences)
            val features = extractFeatures(channel, userPreferences)

            if (score > 0.3f) { // Threshold for relevance
                recommendations.add(ChannelRecommendation(
                    channel = channel,
                    score = score,
                    reason = reason,
                    confidence = score.coerceIn(0f, 1f),
                    features = features
                ))
            }
        }

        recommendations
            .sortedByDescending { it.score }
            .take(limit)
    }

    private fun getOrCreateChannelEmbedding(channel: Channel): ChannelEmbedding {
        // In production, cache these embeddings in Room
        val vector = generateChannelEmbedding(channel)
        return ChannelEmbedding(
            channelId = channel.id,
            vector = vector,
            category = channel.category,
            tags = channel.tags,
            language = detectLanguage(channel),
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun generateChannelEmbedding(channel: Channel): FloatArray {
        // Simplified embedding generation based on channel features
        // In production, use a proper embedding model
        val vector = FloatArray(EMBEDDING_DIM)
        val categoryIndex = ChannelCategory.values().indexOf(channel.category)
        val tagsHash = channel.tags.joinToString("").hashCode()

        for (i in 0 until EMBEDDING_DIM) {
            vector[i] = (Math.sin((categoryIndex + 1) * (i + 1) * 0.1) *
                    Math.cos((tagsHash + i) * 0.01)).toFloat()
        }
        return vector.normalized()
    }

    private fun detectLanguage(channel: Channel): String {
        // Simple heuristic — in production use ML Kit Language ID
        val text = "${channel.name} ${channel.description ?: ""}"
        return if (text.any { it in 'ا'..'ي' }) "ar" else "en"
    }

    private fun computeSimilarity(userVec: FloatArray, channelVec: FloatArray): Float {
        // Cosine similarity
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in userVec.indices) {
            dot += userVec[i] * channelVec[i]
            normA += userVec[i] * userVec[i]
            normB += channelVec[i] * channelVec[i]
        }
        return if (normA > 0 && normB > 0) dot / (Math.sqrt(normA.toDouble()) * Math.sqrt(normB.toDouble())).toFloat() else 0f
    }

    private fun determineReason(
        userEmbedding: UserEmbedding,
        channelEmbedding: ChannelEmbedding,
        prefs: UserPreferences
    ): RecommendationReason {
        if (prefs.preferredCategories.contains(channelEmbedding.category)) {
            return RecommendationReason.CATEGORY_MATCH
        }
        if (channelEmbedding.tags.any { it in userEmbedding.vector.indices }) {
            return RecommendationReason.SIMILAR_INTERESTS
        }
        if (channelEmbedding.updatedAt > System.currentTimeMillis() - 86400000) {
            return RecommendationReason.NEW_CONTENT
        }
        return RecommendationReason.HIGH_ENGAGEMENT
    }

    private fun extractFeatures(channel: Channel, prefs: UserPreferences): RecommendationFeatures {
        return RecommendationFeatures(
            userCategories = prefs.preferredCategories.map { it.name },
            userLanguages = prefs.preferredLanguages,
            recentInteractions = emptyList(), // Would come from local interaction log
            timeOfDay = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
            dayOfWeek = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK),
            isNearby = false, // Would check geo-fence
            friendOverlap = 0, // Would check social graph
            engagementVelocity = 0f // Would compute from growth stats
        )
    }

    private fun fallbackRecommendations(
        channels: List<Channel>,
        prefs: UserPreferences,
        limit: Int
    ): List<ChannelRecommendation> {
        return channels
            .filter { prefs.preferredCategories.isEmpty() || it.category in prefs.preferredCategories }
            .sortedByDescending { it.subscriberCount.toDouble() * (it.growthStats?.velocity ?: 1.0) }
            .take(limit)
            .mapIndexed { index, channel ->
                ChannelRecommendation(
                    channel = channel,
                    score = 1f - (index * 0.05f),
                    reason = RecommendationReason.CATEGORY_MATCH,
                    confidence = 0.7f,
                    features = RecommendationFeatures()
                )
            }
    }

    suspend fun updateUserEmbedding(interactions: List<ChannelInteraction>): UserEmbedding = withContext(Dispatchers.IO) {
        // Incremental embedding update based on user interactions
        val currentVector = UserEmbeddingStorage.get(context)?.vector ?: FloatArray(EMBEDDING_DIM) { 0f }
        val updatedVector = currentVector.copyOf()

        for (interaction in interactions) {
            val channelEmbedding = getOrCreateChannelEmbedding(interaction.channel)
            val weight = when (interaction.type) {
                InteractionType.VIEW -> 0.01f
                InteractionType.JOIN -> 0.1f
                InteractionType.REACT -> 0.05f
                InteractionType.SHARE -> 0.08f
                InteractionType.MUTE -> -0.05f
            }
            for (i in updatedVector.indices) {
                updatedVector[i] += channelEmbedding.vector[i] * weight
            }
        }

        val embedding = UserEmbedding(
            vector = updatedVector.normalized(),
            lastUpdated = System.currentTimeMillis(),
            version = (UserEmbeddingStorage.get(context)?.version ?: 0) + 1
        )
        UserEmbeddingStorage.save(context, embedding)
        embedding
    }

    fun shutdown() {
        interpreter?.close()
        interpreter = null
        isModelLoaded = false
    }
}

private fun FloatArray.normalized(): FloatArray {
    val norm = Math.sqrt(this.map { it * it }.sum()).toFloat()
    return if (norm > 0) this.map { it / norm }.toFloatArray() else this
}

@Serializable
data class ChannelInteraction(
    val channel: Channel,
    val type: InteractionType,
    val timestamp: Long = System.currentTimeMillis()
)

enum class InteractionType {
    VIEW, JOIN, REACT, SHARE, MUTE
}

object UserEmbeddingStorage {
    private const val PREFS_NAME = "ml_discovery_prefs"
    private const val KEY_EMBEDDING = "user_embedding"

    fun save(context: Context, embedding: UserEmbedding) {
        val json = Json { ignoreUnknownKeys = true }
        val jsonString = json.encodeToString(embedding)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EMBEDDING, jsonString)
            .apply()
    }

    fun get(context: Context): UserEmbedding? {
        val jsonString = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_EMBEDDING, null)
        return jsonString?.let { Json { ignoreUnknownKeys = true }.decodeFromString<UserEmbedding>(it) }
    }
}
