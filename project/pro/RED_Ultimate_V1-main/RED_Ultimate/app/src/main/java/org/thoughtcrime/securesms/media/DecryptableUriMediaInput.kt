package com.red.sovereign.media

import android.content.Context
import android.net.Uri
import androidx.annotation.RequiresApi
import com.red.sovereign.database.REDDatabase.Companion.attachments
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.mms.PartAuthority
import com.red.sovereign.mms.PartUriParser
import com.red.sovereign.video.MediaDataSourceProvider
import com.red.sovereign.video.interfaces.MediaInput
import com.red.sovereign.video.interfaces.MediaInputFactory
import com.red.sovereign.video.videoconverter.mediadatasource.MediaDataSourceMediaInput
import java.io.IOException

/**
 * A media input source that is decrypted on the fly.
 */
@RequiresApi(api = 23)
object DecryptableUriMediaInput : MediaInputFactory {
  @Throws(IOException::class)
  override fun createForUri(context: Context, uri: Uri): MediaInput {
    if (AppDependencies.blobs.isAuthority(uri)) {
      return MediaDataSourceMediaInput(MediaDataSourceProvider.getMediaDataSource(context, uri))
    }
    return if (PartAuthority.isLocalUri(uri)) {
      createForAttachmentUri(uri)
    } else {
      UriMediaInput(context, uri)
    }
  }

  private fun createForAttachmentUri(uri: Uri): MediaInput {
    val partId = PartUriParser(uri).partId
    if (!partId.isValid) {
      throw AssertionError()
    }
    val mediaDataSource = attachments.mediaDataSourceFor(partId, true) ?: throw AssertionError()
    return MediaDataSourceMediaInput(mediaDataSource)
  }
}
