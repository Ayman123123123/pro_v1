package com.red.server.media.v1

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The old chunked/transcode endpoints referenced missing metadata storage,
 * incomplete MinIO compose logic and an unimplemented `canAccess` check. No
 * active Android or admin client calls them. Reject all v1 media requests
 * rather than pretending a 500 GB upload or returning unguarded object bytes.
 * The supported upload/download endpoints are under /api/media.
 */
@RestController
@RequestMapping("/api/v1/media")
class MediaV1Controller {
    @RequestMapping(path = ["", "/", "/{*path}"])
    fun gone(): ResponseEntity<Map<String, String>> = ResponseEntity.status(HttpStatus.GONE).body(
        mapOf("error" to "GONE", "message" to "Legacy media v1 is unavailable; use /api/media")
    )
}
