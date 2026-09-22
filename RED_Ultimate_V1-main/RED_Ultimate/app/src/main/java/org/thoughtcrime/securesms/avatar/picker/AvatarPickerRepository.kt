package com.red.sovereign.avatar.picker

import android.content.Context
import android.net.Uri
import android.widget.Toast
import io.reactivex.rxjava3.core.Single
import org.signal.core.models.media.Media
import org.signal.core.util.StreamUtil
import org.signal.core.util.ThreadUtil
import org.signal.core.util.concurrent.REDExecutors
import org.signal.core.util.logging.Log
import com.red.sovereign.R
import com.red.sovereign.avatar.Avatar
import com.red.sovereign.avatar.AvatarPickerStorage
import com.red.sovereign.avatar.AvatarRenderer
import com.red.sovereign.avatar.Avatars
import com.red.sovereign.conversation.colors.AvatarColor
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.groups.GroupId
import com.red.sovereign.profiles.AvatarHelper
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.util.NameUtil
import org.whispersystems.signalservice.api.util.StreamDetails
import java.io.IOException

private val TAG = Log.tag(AvatarPickerRepository::class.java)

class AvatarPickerRepository(context: Context) {

  private val applicationContext = context.applicationContext

  fun getAvatarForSelf(): Single<Avatar> = Single.fromCallable {
    val details: StreamDetails? = AvatarHelper.getSelfProfileAvatarStream(applicationContext)
    if (details != null) {
      try {
        val bytes = StreamUtil.readFully(details.stream)
        Avatar.Photo(
          AppDependencies.blobs.forData(bytes).createForSingleSessionInMemory(),
          details.length,
          Avatar.DatabaseId.DoNotPersist
        )
      } catch (e: IOException) {
        Log.w(TAG, "Failed to read avatar!")
        getDefaultAvatarForSelf()
      }
    } else {
      getDefaultAvatarForSelf()
    }
  }

  fun getAvatarForGroup(groupId: GroupId): Single<Avatar> = Single.fromCallable {
    val recipient = Recipient.externalGroupExact(groupId)

    if (AvatarHelper.hasAvatar(applicationContext, recipient.id)) {
      try {
        val bytes = AvatarHelper.getAvatarBytes(applicationContext, recipient.id)
        Avatar.Photo(
          AppDependencies.blobs.forData(bytes).createForSingleSessionInMemory(),
          AvatarHelper.getAvatarLength(applicationContext, recipient.id),
          Avatar.DatabaseId.DoNotPersist
        )
      } catch (e: IOException) {
        Log.w(TAG, "Failed to read group avatar!")
        getDefaultAvatarForGroup(recipient.avatarColor)
      }
    } else {
      getDefaultAvatarForGroup(recipient.avatarColor)
    }
  }

  fun getPersistedAvatarsForSelf(): Single<List<Avatar>> = Single.fromCallable {
    REDDatabase.avatarPicker.getAvatarsForSelf()
  }

  fun getPersistedAvatarsForGroup(groupId: GroupId): Single<List<Avatar>> = Single.fromCallable {
    REDDatabase.avatarPicker.getAvatarsForGroup(groupId)
  }

  fun getDefaultAvatarsForSelf(): Single<List<Avatar>> = Single.fromCallable {
    Avatars.defaultAvatarsForSelf.entries.mapIndexed { index, entry ->
      Avatar.Vector(entry.key, color = Avatars.colors[index % Avatars.colors.size], Avatar.DatabaseId.NotSet)
    }
  }

  fun getDefaultAvatarsForGroup(): Single<List<Avatar>> = Single.fromCallable {
    Avatars.defaultAvatarsForGroup.entries.mapIndexed { index, entry ->
      Avatar.Vector(entry.key, color = Avatars.colors[index % Avatars.colors.size], Avatar.DatabaseId.NotSet)
    }
  }

  fun writeMediaToMultiSessionStorage(media: Media, onMediaWrittenToMultiSessionStorage: (Uri) -> Unit) {
    REDExecutors.BOUNDED.execute {
      onMediaWrittenToMultiSessionStorage(AvatarPickerStorage.save(applicationContext, media))
    }
  }

  fun persistAvatarForSelf(avatar: Avatar, onPersisted: (Avatar) -> Unit) {
    REDExecutors.BOUNDED.execute {
      val avatarDatabase = REDDatabase.avatarPicker
      val savedAvatar = avatarDatabase.saveAvatarForSelf(avatar)
      avatarDatabase.markUsage(savedAvatar)
      onPersisted(savedAvatar)
    }
  }

  fun persistAvatarForGroup(avatar: Avatar, groupId: GroupId, onPersisted: (Avatar) -> Unit) {
    REDExecutors.BOUNDED.execute {
      val avatarDatabase = REDDatabase.avatarPicker
      val savedAvatar = avatarDatabase.saveAvatarForGroup(avatar, groupId)
      avatarDatabase.markUsage(savedAvatar)
      onPersisted(savedAvatar)
    }
  }

  fun persistAndCreateMediaForSelf(avatar: Avatar, onSaved: (Media) -> Unit) {
    REDExecutors.BOUNDED.execute {
      if (avatar.databaseId !is Avatar.DatabaseId.DoNotPersist) {
        persistAvatarForSelf(avatar) {
          AvatarRenderer.renderAvatar(applicationContext, avatar, onSaved, this::handleRenderFailure)
        }
      } else {
        AvatarRenderer.renderAvatar(applicationContext, avatar, onSaved, this::handleRenderFailure)
      }
    }
  }

  fun persistAndCreateMediaForGroup(avatar: Avatar, groupId: GroupId, onSaved: (Media) -> Unit) {
    REDExecutors.BOUNDED.execute {
      if (avatar.databaseId !is Avatar.DatabaseId.DoNotPersist) {
        persistAvatarForGroup(avatar, groupId) {
          AvatarRenderer.renderAvatar(applicationContext, avatar, onSaved, this::handleRenderFailure)
        }
      } else {
        AvatarRenderer.renderAvatar(applicationContext, avatar, onSaved, this::handleRenderFailure)
      }
    }
  }

  fun createMediaForNewGroup(avatar: Avatar, onSaved: (Media) -> Unit) {
    REDExecutors.BOUNDED.execute {
      AvatarRenderer.renderAvatar(applicationContext, avatar, onSaved, this::handleRenderFailure)
    }
  }

  fun handleRenderFailure(throwable: Throwable?) {
    Log.w(TAG, "Failed to render avatar.", throwable)
    ThreadUtil.postToMain {
      Toast.makeText(applicationContext, R.string.AvatarPickerRepository__failed_to_save_avatar, Toast.LENGTH_SHORT).show()
    }
  }

  fun getDefaultAvatarForSelf(): Avatar {
    val initials = NameUtil.getAbbreviation(Recipient.self().getDisplayName(applicationContext))

    return if (initials.isNullOrBlank()) {
      Avatar.getDefaultForSelf()
    } else {
      Avatar.Text(initials, requireNotNull(Avatars.colorMap[Recipient.self().avatarColor.serialize()]), Avatar.DatabaseId.DoNotPersist)
    }
  }

  fun getDefaultAvatarForGroup(groupId: GroupId): Avatar {
    val recipient = Recipient.externalGroupExact(groupId)

    return getDefaultAvatarForGroup(recipient.avatarColor)
  }

  fun getDefaultAvatarForGroup(color: AvatarColor?): Avatar {
    val colorPair = Avatars.colorMap[color?.serialize()]
    val defaultColor = Avatar.getDefaultForGroup()

    return if (colorPair != null) {
      defaultColor.copy(color = colorPair)
    } else {
      defaultColor
    }
  }

  fun delete(avatar: Avatar, onDelete: () -> Unit) {
    REDExecutors.BOUNDED.execute {
      if (avatar.databaseId is Avatar.DatabaseId.Saved) {
        val avatarDatabase = REDDatabase.avatarPicker
        avatarDatabase.deleteAvatar(avatar)
      }
      onDelete()
    }
  }
}
