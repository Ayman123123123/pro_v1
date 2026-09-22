package com.red.sovereign.components.settings.app.notifications.profiles.models

import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.SimpleColorFilter
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.google.android.material.materialswitch.MaterialSwitch
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.horizontalGutters
import com.red.sovereign.R
import com.red.sovereign.components.emoji.EmojiUtil
import com.red.sovereign.components.settings.DSLSettingsIcon
import com.red.sovereign.components.settings.DSLSettingsText
import com.red.sovereign.components.settings.PreferenceModel
import com.red.sovereign.components.settings.PreferenceViewHolder
import com.red.sovereign.conversation.colors.AvatarColor
import com.red.sovereign.notifications.profiles.NotificationProfile
import com.red.sovereign.notifications.profiles.NotificationProfileId
import com.red.sovereign.notifications.profiles.NotificationProfileSchedule
import com.red.sovereign.notifications.profiles.NotificationProfiles
import com.red.sovereign.util.adapter.mapping.LayoutFactory
import com.red.sovereign.util.adapter.mapping.MappingAdapter
import com.red.sovereign.util.visible
import java.util.UUID

/**
 * DSL custom preference for showing Notification Profile rows.
 */
object NotificationProfilePreference {

  fun register(adapter: MappingAdapter) {
    adapter.registerFactory(Model::class.java, LayoutFactory(::ViewHolder, R.layout.notification_profile_preference_item))
  }

  class Model(
    override val title: DSLSettingsText,
    override val summary: DSLSettingsText?,
    override val icon: DSLSettingsIcon?,
    val color: AvatarColor,
    val isOn: Boolean = false,
    val showSwitch: Boolean = false,
    val onClick: () -> Unit
  ) : PreferenceModel<Model>()

  private class ViewHolder(itemView: View) : PreferenceViewHolder<Model>(itemView) {

    private val switchWidget: MaterialSwitch = itemView.findViewById(R.id.switch_widget)

    override fun bind(model: Model) {
      super.bind(model)
      itemView.setOnClickListener { model.onClick() }
      switchWidget.setOnCheckedChangeListener(null)
      switchWidget.visible = model.showSwitch
      switchWidget.isEnabled = model.isEnabled
      switchWidget.isChecked = model.isOn
      iconView.background.colorFilter = SimpleColorFilter(model.color.colorInt())
      switchWidget.setOnCheckedChangeListener { _, _ -> model.onClick() }
    }
  }
}

@Composable
fun NotificationProfileRow(
  profile: NotificationProfile,
  isActiveProfile: Boolean = false,
  showSwitch: Boolean = false,
  enabled: Boolean = true,
  onClick: (Long) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  Row(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = { onClick(profile.id) })
      .horizontalGutters()
      .padding(vertical = 16.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .background(
          color = Color(profile.color.colorInt()),
          shape = CircleShape
        )
        .padding(8.dp),
      contentAlignment = Alignment.Center
    ) {
      if (profile.emoji.isNotEmpty()) {
        val emojiDrawable = remember(profile.emoji) { EmojiUtil.convertToDrawable(context, profile.emoji) }

        Image(
          painter = rememberDrawablePainter(drawable = emojiDrawable),
          contentDescription = null,
          modifier = Modifier.size(24.dp)
        )
      } else {
        Image(
          imageVector = ImageVector.vectorResource(R.drawable.ic_moon_24),
          contentDescription = null,
          modifier = Modifier.size(24.dp)
        )
      }
    }

    Spacer(modifier = Modifier.width(16.dp))

    Column(
      modifier = Modifier.weight(1f)
    ) {
      Text(
        text = profile.name,
        style = MaterialTheme.typography.bodyLarge
      )

      val summary = remember(isActiveProfile) {
        if (isActiveProfile) {
          NotificationProfiles.getActiveProfileDescription(context, profile)
        } else {
          null
        }
      }

      if (summary != null) {
        Text(
          text = summary,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    if (showSwitch) {
      Switch(
        checked = isActiveProfile,
        onCheckedChange = { onClick(profile.id) },
        enabled = enabled
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun NotificationProfileRowPreview() {
  Previews.Preview {
    Column {
      NotificationProfileRow(
        profile = NotificationProfile(
          id = 1L,
          name = "Work",
          createdAt = 0L,
          schedule = NotificationProfileSchedule(
            id = 1L
          ),
          emoji = "",
          notificationProfileId = NotificationProfileId(UUID.randomUUID())
        ),
        onClick = {}
      )

      NotificationProfileRow(
        profile = NotificationProfile(
          id = 1L,
          name = "Sleep",
          createdAt = 0L,
          schedule = NotificationProfileSchedule(
            id = 1L
          ),
          emoji = "",
          notificationProfileId = NotificationProfileId(UUID.randomUUID())
        ),
        onClick = {}
      )
    }
  }
}
