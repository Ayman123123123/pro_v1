package com.red.server.media

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.util.UUID

@RestController
@RequestMapping("/api/media")
class MediaController(
    private val media: MediaService,
    private val access: MediaAccessService,
    private val grants: MediaGrantService
) {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(@RequestPart("file") file: MultipartFile, authentication: Authentication) =
        media.upload(UUID.fromString(authentication.name), file)

    @PostMapping("/grants")
    fun grant(@RequestBody request: MediaGrantRequest, authentication: Authentication) =
        grants.grant(UUID.fromString(authentication.name), request)

    @DeleteMapping("/users/{userId}/{fileName:.+}")
    fun delete(@PathVariable userId: String, @PathVariable fileName: String, authentication: Authentication): ResponseEntity<Void> {
        val ownerId = UUID.fromString(authentication.name)
        require(userId == ownerId.toString()) { "Only the media owner can delete this object" }
        val key = "users/$userId/$fileName"
        grants.revokeAll(ownerId, key)
        media.delete(key)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/users/{userId}/{fileName:.+}/thumb")
    fun thumbnail(
        @PathVariable userId: String,
        @PathVariable fileName: String,
        authentication: Authentication
    ): ResponseEntity<StreamingResponseBody> {
        val key = "users/$userId/$fileName"
        access.requireDownloadAllowed(java.util.UUID.fromString(authentication.name), key)
        val thumbKey = media.generateThumbnail(key)
        val metadata = media.metadata(thumbKey)
        val body = StreamingResponseBody { output -> media.stream(thumbKey, output) }
        return ResponseEntity.ok()
            .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
            .contentType(runCatching { org.springframework.http.MediaType.parseMediaType(metadata.mimeType) }.getOrDefault(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM))
            .body(body)
    }

    @GetMapping("/users/{userId}/{fileName:.+}")
    fun download(
        @PathVariable userId: String,
        @PathVariable fileName: String,
        authentication: Authentication
    ): ResponseEntity<StreamingResponseBody> {
        val key = "users/$userId/$fileName"
        access.requireDownloadAllowed(UUID.fromString(authentication.name), key)
        val metadata = media.metadata(key)
        val body = StreamingResponseBody { output -> media.stream(key, output) }
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
            .contentLength(metadata.size)
            .contentType(runCatching { MediaType.parseMediaType(metadata.mimeType) }.getOrDefault(MediaType.APPLICATION_OCTET_STREAM))
            .body(body)
    }
}
