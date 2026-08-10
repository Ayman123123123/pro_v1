package com.red.sovereign.crypto

import android.content.Context
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.DeviceKeyManager
import com.red.sovereign.auth.TokenStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Base64

@Serializable data class EcPreKeyUpload(val keyId: Int, val publicKey: String)
@Serializable data class KyberPreKeyUpload(val keyId: Int, val publicKey: String, val signature: String)
@Serializable data class PreKeyUpload(
    val ecPreKeys: List<EcPreKeyUpload>,
    val kyberPreKeys: List<KyberPreKeyUpload>
)
@Serializable data class PreKeyStock(val ecAvailable: Int, val kyberAvailable: Int, val minimumRecommended: Int = 20)

/** Maintains an atomic server-visible stock while every private pre-key remains encrypted on this device. */
class PreKeyPoolManager(context: Context) {
    private val tokens = TokenStore(context)
    private val keys = DeviceKeyManager(context)
    private val protocolStore = PersistentSignalProtocolStore(context, keys)
    private val client = AuthorizedApiClient(tokens)
    private val json = Json { ignoreUnknownKeys = true }
    private val encoder = Base64.getEncoder()

    suspend fun replenishIfNeeded(): ApiResult<PreKeyStock> {
        val deviceId = tokens.deviceId ?: return ApiResult.Error(401, "DEVICE_ID_MISSING")
        val stockResult = client.request("GET", "/api/devices/$deviceId/prekeys/stock")
        if (stockResult is ApiResult.Error) return stockResult
        val stockSuccess = stockResult as ApiResult.Success
        val stock = runCatching { json.decodeFromString<PreKeyStock>(stockSuccess.value) }
            .getOrElse { return ApiResult.Error(null, "INVALID_PREKEY_STOCK_RESPONSE") }
        val availablePairs = minOf(stock.ecAvailable, stock.kyberAvailable)
        if (availablePairs >= stock.minimumRecommended) return ApiResult.Success(stockSuccess.code, stock)

        val batch = protocolStore.generateOneTimeBatch((TARGET_STOCK - availablePairs).coerceIn(1, MAX_BATCH))
        val upload = PreKeyUpload(
            ecPreKeys = batch.ec.map { EcPreKeyUpload(it.id, encoder.encodeToString(it.keyPair.publicKey.serialize())) },
            kyberPreKeys = batch.kyber.map {
                val publicKey = it.keyPair.publicKey.serialize()
                KyberPreKeyUpload(it.id, encoder.encodeToString(publicKey), encoder.encodeToString(keys.sign(publicKey)))
            }
        )
        val uploaded = client.request("POST", "/api/devices/$deviceId/prekeys", json.encodeToString(upload))
        if (uploaded is ApiResult.Error) return uploaded
        val uploadSuccess = uploaded as ApiResult.Success
        return runCatching {
            ApiResult.Success(uploadSuccess.code, json.decodeFromString<PreKeyStock>(uploadSuccess.value))
        }.getOrElse { ApiResult.Error(null, "INVALID_PREKEY_UPLOAD_RESPONSE") }
    }

    private companion object {
        const val TARGET_STOCK = 50
        const val MAX_BATCH = 100
    }
}
