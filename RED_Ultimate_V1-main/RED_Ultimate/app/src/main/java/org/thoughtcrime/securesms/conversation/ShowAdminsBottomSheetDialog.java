package com.red.sovereign.conversation;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.signal.core.models.ServiceId;
import com.red.sovereign.R;
import com.red.sovereign.conversation.colors.ColorizerV2;
import com.red.sovereign.conversation.colors.NameColor;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.database.model.GroupRecord;
import com.red.sovereign.groups.GroupId;
import com.red.sovereign.groups.memberlabel.MemberLabel;
import com.red.sovereign.groups.memberlabel.MemberLabelRepository;
import com.red.sovereign.groups.ui.GroupMemberEntry;
import com.red.sovereign.groups.ui.GroupMemberListView;
import com.red.sovereign.recipients.Recipient;
import com.red.sovereign.recipients.RecipientId;
import org.signal.core.ui.BottomSheetUtil;
import com.red.sovereign.util.CommunicationActions;
import org.signal.core.util.concurrent.LifecycleDisposable;
import com.red.sovereign.util.WindowUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Renders a list of admins for a specified groupId. Tapping on one will allow you to send them a message.
 */
public final class ShowAdminsBottomSheetDialog extends BottomSheetDialogFragment {

  private static final String KEY_GROUP_ID = "group_id";

  private final LifecycleDisposable disposables = new LifecycleDisposable();

  public static void show(@NonNull FragmentManager manager, @NonNull GroupId.V2 groupId) {
    ShowAdminsBottomSheetDialog fragment = new ShowAdminsBottomSheetDialog();

    Bundle args = new Bundle();
    args.putParcelable(KEY_GROUP_ID, groupId);
    fragment.setArguments(args);

    fragment.show(manager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG);
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    setStyle(DialogFragment.STYLE_NORMAL, R.style.RED_DayNight_BottomSheet_Rounded);
    super.onCreate(savedInstanceState);
  }

  @Override
  public @NonNull View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.show_admin_bottom_sheet, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    disposables.bindTo(getViewLifecycleOwner().getLifecycle());

    GroupMemberListView list = view.findViewById(R.id.show_admin_list);
    list.initializeAdapter(getViewLifecycleOwner());
    list.setMembers(Collections.emptyList());

    list.setRecipientClickListener(recipient -> {
      CommunicationActions.startConversation(requireContext(), recipient, null);
      dismissAllowingStateLoss();
    });

    disposables.add(Single.fromCallable(() -> getAdmins(requireContext().getApplicationContext(), getGroupId()))
                          .subscribeOn(Schedulers.io())
                          .observeOn(AndroidSchedulers.mainThread())
                          .subscribe(list::setMembers));
  }

  @Override
  public void onResume() {
    super.onResume();
    WindowUtil.initializeScreenshotSecurity(requireContext(), requireDialog().getWindow());
  }

  @Override
  public void show(@NonNull FragmentManager manager, @Nullable String tag) {
    BottomSheetUtil.show(manager, tag, this);
  }

  private GroupId getGroupId() {
    return requireArguments().getParcelable(KEY_GROUP_ID);
  }

  @WorkerThread
  private static @NonNull List<GroupMemberEntry> getAdmins(@NonNull Context context, @NonNull GroupId groupId) {
    GroupRecord groupRecord = REDDatabase.groups().getGroup(groupId).orElse(null);
    if (groupRecord == null || !groupRecord.getHasV2GroupProperties()) {
      return Collections.emptyList();
    }

    List<Recipient>               admins              = groupRecord.getAdmins();
    Map<RecipientId, MemberLabel> labelsByRecipientId = MemberLabelRepository.getInstance().getLabelsSync(groupId.requireV2(), admins);
    List<ServiceId>               memberIds           = groupRecord.requireV2GroupProperties().getMemberServiceIds();
    ColorizerV2                   colorizer           = new ColorizerV2(memberIds);

    List<GroupMemberEntry> result = new ArrayList<>();
    for (Recipient admin : admins) {
      MemberLabel label     = labelsByRecipientId.get(admin.getId());
      NameColor   nameColor = label != null ? colorizer.getNameColor(context, admin) : null;
      result.add(new GroupMemberEntry.FullMember(admin, true, label, nameColor));
    }
    return result;
  }
}
