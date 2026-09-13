package com.red.sovereign.core.network

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class MinioUploader(private val client: OkHttpClient) {
    
    fun uploadFile(file: File, uploadUrl: String, callback: (Boolean, String?) -> Unit) {
        val requestBody = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(uploadUrl)
            .put(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                callback(false, e.message)
            }

            override fun onResponse(call: Call, response: Response) {
                // Response must be closed to release the connection back to the pool —
                // `use{}` guarantees close even if callback throws.
                response.use {
                    callback(it.isSuccessful, if (it.isSuccessful) uploadUrl else null)
                }
            }
        })
    }
}
