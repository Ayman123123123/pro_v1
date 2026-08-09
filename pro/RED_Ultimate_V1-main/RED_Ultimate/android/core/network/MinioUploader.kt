package com.red.sovereign.core.network

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

class MinioUploader(private val client: OkHttpClient) {
    companion object { private const val TAG = "RED.MinioUploader" }

    fun uploadFile(file: File, uploadUrl: String, callback: (Boolean, String?) -> Unit) {
        val requestBody = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(uploadUrl)
            .put(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Upload failed for ${file.name}", e)
                callback(false, e.message)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.i(TAG, "Uploaded ${file.name} (${file.length()} bytes) successfully")
                    callback(true, uploadUrl)
                } else {
                    val code = response.code
                    Log.w(TAG, "Upload rejected with HTTP $code for ${file.name}")
                    callback(false, "HTTP $code")
                }
            }
        })
    }

    /**
     * Synchronous upload for use in coroutines (e.g., story upload).
     * Returns the URL on success, null on failure.
     */
    fun uploadFileSync(file: File, objectKey: String): String? {
        return try {
            val requestBody = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(objectKey)  // Full URL passed as objectKey
                .put(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.i(TAG, "Sync uploaded ${file.name} (${file.length()} bytes)")
                    objectKey
                } else {
                    Log.w(TAG, "Sync upload failed: HTTP ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync upload exception for ${file.name}", e)
            null
        }
    }
}
