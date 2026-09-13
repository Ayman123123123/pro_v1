package com.red.sovereign.crypto

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.red.sovereign.auth.DeviceKeyManager
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.ReusedBaseKeyException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/** Durable libsignal 0.86.5 store. All records contain cryptographic state, never plaintext messages. */
class PersistentSignalProtocolStore(context: Context, private val keys: DeviceKeyManager = DeviceKeyManager(context)) :
    SQLiteOpenHelper(context, "red_signal_protocol.db", null, 1), SignalProtocolStore {
    private val cipher = ProtocolRecordCipher()

    init {
        writableDatabase
        val signed = keys.signedPreKeyRecord(); if (!containsSignedPreKey(signed.id)) storeSignedPreKey(signed.id, signed)
        val kyber = keys.kyberPreKeyRecord(); if (!containsKyberPreKey(kyber.id)) storeKyberPreKey(kyber.id, kyber)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE identities(name TEXT NOT NULL, device INTEGER NOT NULL, record BLOB NOT NULL, PRIMARY KEY(name,device))")
        db.execSQL("CREATE TABLE prekeys(id INTEGER PRIMARY KEY, record BLOB NOT NULL)")
        db.execSQL("CREATE TABLE signed_prekeys(id INTEGER PRIMARY KEY, record BLOB NOT NULL)")
        db.execSQL("CREATE TABLE kyber_prekeys(id INTEGER PRIMARY KEY, record BLOB NOT NULL)")
        db.execSQL("CREATE TABLE kyber_usage(kyber_id INTEGER NOT NULL, signed_id INTEGER NOT NULL, base_key BLOB NOT NULL, PRIMARY KEY(kyber_id,signed_id,base_key))")
        db.execSQL("CREATE TABLE sessions(name TEXT NOT NULL, device INTEGER NOT NULL, record BLOB NOT NULL, PRIMARY KEY(name,device))")
        db.execSQL("CREATE TABLE sender_keys(name TEXT NOT NULL, device INTEGER NOT NULL, distribution TEXT NOT NULL, record BLOB NOT NULL, PRIMARY KEY(name,device,distribution))")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    override fun getIdentityKeyPair(): IdentityKeyPair = keys.identityKeyPair()
    override fun getLocalRegistrationId(): Int = keys.registrationId()

    @Synchronized
    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): IdentityKeyStore.IdentityChange {
        val previous = getIdentity(address)
        writableDatabase.insertWithOnConflict("identities", null, ContentValues().apply {
            put("name", address.name); put("device", address.deviceId); put("record", cipher.encrypt(identityKey.serialize()))
        }, SQLiteDatabase.CONFLICT_REPLACE)
        return if (previous != null && !previous.serialize().contentEquals(identityKey.serialize()))
            IdentityKeyStore.IdentityChange.REPLACED_EXISTING else IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
    }

    override fun isTrustedIdentity(address: SignalProtocolAddress, identityKey: IdentityKey, direction: IdentityKeyStore.Direction): Boolean =
        getIdentity(address)?.serialize()?.contentEquals(identityKey.serialize()) ?: true

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? = blob("identities", "name = ? AND device = ?", arrayOf(address.name, address.deviceId.toString()))?.let(::IdentityKey)

    override fun loadPreKey(preKeyId: Int): PreKeyRecord = blob("prekeys", "id = ?", arrayOf(preKeyId.toString()))?.let(::PreKeyRecord)
        ?: throw InvalidKeyIdException("No EC pre-key $preKeyId")
    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) = putId("prekeys", preKeyId, record.serialize())
    override fun containsPreKey(preKeyId: Int) = exists("prekeys", "id = ?", arrayOf(preKeyId.toString()))
    override fun removePreKey(preKeyId: Int) { writableDatabase.delete("prekeys", "id = ?", arrayOf(preKeyId.toString())) }

    /** Creates fresh private records locally; callers upload public material only. */
    @Synchronized
    fun generateOneTimeBatch(count: Int): OneTimePreKeyBatch {
        require(count in 1..100)
        val random = SecureRandom()
        val ec = ArrayList<PreKeyRecord>(count)
        val kyber = ArrayList<KyberPreKeyRecord>(count)
        repeat(count) {
            var ecId: Int
            do ecId = random.nextInt(Int.MAX_VALUE) while (containsPreKey(ecId))
            PreKeyRecord(ecId, ECKeyPair.generate()).also { storePreKey(ecId, it); ec += it }

            var kyberId: Int
            do kyberId = random.nextInt(Int.MAX_VALUE) while (containsKyberPreKey(kyberId))
            val pair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
            val signature = keys.sign(pair.publicKey.serialize())
            KyberPreKeyRecord(kyberId, System.currentTimeMillis(), pair, signature)
                .also { storeKyberPreKey(kyberId, it); kyber += it }
        }
        return OneTimePreKeyBatch(ec, kyber)
    }

    override fun loadSession(address: SignalProtocolAddress): SessionRecord = blob("sessions", "name = ? AND device = ?", arrayOf(address.name, address.deviceId.toString()))?.let(::SessionRecord) ?: SessionRecord()
    override fun loadExistingSessions(addresses: List<SignalProtocolAddress>): List<SessionRecord> = addresses.map { address ->
        if (!containsSession(address)) throw NoSessionException(address, "No session for $address")
        loadSession(address)
    }
    override fun getSubDeviceSessions(name: String): List<Int> {
        val result = mutableListOf<Int>()
        readableDatabase.query("sessions", arrayOf("device"), "name = ?", arrayOf(name), null, null, "device").use { while (it.moveToNext()) result += it.getInt(0) }
        return result
    }
    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        writableDatabase.insertWithOnConflict("sessions", null, ContentValues().apply { put("name", address.name); put("device", address.deviceId); put("record", cipher.encrypt(record.serialize())) }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    override fun containsSession(address: SignalProtocolAddress) = exists("sessions", "name = ? AND device = ?", arrayOf(address.name, address.deviceId.toString()))
    override fun deleteSession(address: SignalProtocolAddress) { writableDatabase.delete("sessions", "name = ? AND device = ?", arrayOf(address.name, address.deviceId.toString())) }
    override fun deleteAllSessions(name: String) { writableDatabase.delete("sessions", "name = ?", arrayOf(name)) }

    override fun loadSignedPreKey(id: Int): SignedPreKeyRecord = blob("signed_prekeys", "id = ?", arrayOf(id.toString()))?.let(::SignedPreKeyRecord)
        ?: throw InvalidKeyIdException("No signed pre-key $id")
    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> = blobs("signed_prekeys").map(::SignedPreKeyRecord)
    override fun storeSignedPreKey(id: Int, record: SignedPreKeyRecord) = putId("signed_prekeys", id, record.serialize())
    override fun containsSignedPreKey(id: Int) = exists("signed_prekeys", "id = ?", arrayOf(id.toString()))
    override fun removeSignedPreKey(id: Int) { writableDatabase.delete("signed_prekeys", "id = ?", arrayOf(id.toString())) }

    override fun loadKyberPreKey(id: Int): KyberPreKeyRecord = blob("kyber_prekeys", "id = ?", arrayOf(id.toString()))?.let(::KyberPreKeyRecord)
        ?: throw InvalidKeyIdException("No Kyber pre-key $id")
    override fun loadKyberPreKeys(): List<KyberPreKeyRecord> = blobs("kyber_prekeys").map(::KyberPreKeyRecord)
    override fun storeKyberPreKey(id: Int, record: KyberPreKeyRecord) = putId("kyber_prekeys", id, record.serialize())
    override fun containsKyberPreKey(id: Int) = exists("kyber_prekeys", "id = ?", arrayOf(id.toString()))
    override fun markKyberPreKeyUsed(kyberPreKeyId: Int, signedPreKeyId: Int, baseKey: ECPublicKey) {
        val inserted = writableDatabase.insertWithOnConflict("kyber_usage", null, ContentValues().apply {
            put("kyber_id", kyberPreKeyId); put("signed_id", signedPreKeyId); put("base_key", MessageDigest.getInstance("SHA-256").digest(baseKey.serialize()))
        }, SQLiteDatabase.CONFLICT_IGNORE)
        if (inserted == -1L) throw ReusedBaseKeyException("Kyber pre-key tuple was reused")
        // The enrollment Kyber key is the explicit last-resort key; one-time Kyber keys are deleted after use.
        if (kyberPreKeyId != keys.kyberPreKeyRecord().id) {
            writableDatabase.delete("kyber_prekeys", "id = ?", arrayOf(kyberPreKeyId.toString()))
        }
    }

    override fun storeSenderKey(sender: SignalProtocolAddress, distributionId: UUID, record: SenderKeyRecord) {
        writableDatabase.insertWithOnConflict("sender_keys", null, ContentValues().apply {
            put("name", sender.name); put("device", sender.deviceId); put("distribution", distributionId.toString()); put("record", cipher.encrypt(record.serialize()))
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    override fun loadSenderKey(sender: SignalProtocolAddress, distributionId: UUID): SenderKeyRecord? =
        blob("sender_keys", "name = ? AND device = ? AND distribution = ?", arrayOf(sender.name, sender.deviceId.toString(), distributionId.toString()))?.let(::SenderKeyRecord)

    private fun putId(table: String, id: Int, bytes: ByteArray) {
        writableDatabase.insertWithOnConflict(table, null, ContentValues().apply { put("id", id); put("record", cipher.encrypt(bytes)) }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    private fun exists(table: String, where: String, args: Array<String>): Boolean = readableDatabase.query(table, arrayOf("1"), where, args, null, null, null, "1").use { it.moveToFirst() }
    private fun blob(table: String, where: String, args: Array<String>): ByteArray? = readableDatabase.query(table, arrayOf("record"), where, args, null, null, null, "1").use { if (it.moveToFirst()) cipher.decrypt(it.getBlob(0)) else null }
    private fun blobs(table: String): List<ByteArray> {
        val result = mutableListOf<ByteArray>(); readableDatabase.query(table, arrayOf("record"), null, null, null, null, "id").use { while (it.moveToNext()) result += cipher.decrypt(it.getBlob(0)) }; return result
    }
}

data class OneTimePreKeyBatch(
    val ec: List<PreKeyRecord>,
    val kyber: List<KyberPreKeyRecord>
)
