package com.red.sovereign.stories.settings.my

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.signal.core.ui.FixedRoundedCornerBottomSheetDialogFragment
import com.red.sovereign.R
import com.red.sovereign.util.SpanUtil

class REDConnectionsBottomSheetDialogFragment : FixedRoundedCornerBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 1f

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    val view = inflater.inflate(R.layout.stories_signal_connection_bottom_sheet, container, false)
    view.findViewById<TextView>(R.id.text_1).text = SpanUtil.boldSubstring(getString(R.string.REDConnectionsBottomSheet__signal_connections_are_people), getString(R.string.REDConnectionsBottomSheet___signal_connections))
    return view
  }
}
