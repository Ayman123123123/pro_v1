package com.red.sovereign.components.settings.conversation

import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import org.signal.core.util.dp
import com.red.sovereign.R
import com.red.sovereign.components.menu.ActionItem
import com.red.sovereign.components.menu.REDContextMenu
import java.util.concurrent.TimeUnit

object MuteContextMenu {

  @JvmStatic
  fun show(anchor: View, container: ViewGroup, fragmentManager: FragmentManager, lifecycleOwner: LifecycleOwner, action: (Long) -> Unit): REDContextMenu {
    fragmentManager.setFragmentResultListener(MuteUntilTimePickerBottomSheet.REQUEST_KEY, lifecycleOwner) { _, bundle ->
      action(bundle.getLong(MuteUntilTimePickerBottomSheet.RESULT_TIMESTAMP))
    }

    val context = anchor.context
    val actionItems = listOf(
      ActionItem(R.drawable.ic_daytime_24, context.getString(R.string.arrays__mute_for_one_hour)) {
        action(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1))
      },
      ActionItem(R.drawable.ic_nighttime_26, context.getString(R.string.arrays__mute_for_eight_hours)) {
        action(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(8))
      },
      ActionItem(R.drawable.symbol_calendar_one, context.getString(R.string.arrays__mute_for_one_day)) {
        action(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1))
      },
      ActionItem(R.drawable.symbol_calendar_week, context.getString(R.string.arrays__mute_for_seven_days)) {
        action(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7))
      },
      ActionItem(R.drawable.symbol_calendar_24, context.getString(R.string.MuteDialog__mute_until)) {
        MuteUntilTimePickerBottomSheet.show(fragmentManager)
      },
      ActionItem(R.drawable.symbol_bell_slash_24, context.getString(R.string.arrays__always)) {
        action(Long.MAX_VALUE)
      }
    )

    return REDContextMenu.Builder(anchor, container)
      .offsetX(12.dp)
      .offsetY(12.dp)
      .preferredVerticalPosition(REDContextMenu.VerticalPosition.ABOVE)
      .show(actionItems)
  }
}
