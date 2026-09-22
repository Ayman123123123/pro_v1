package com.red.sovereign.payments.preferences;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.navigation.Navigation;

import org.signal.core.util.concurrent.SimpleTask;
import com.red.sovereign.ContactSelectionListFragment;
import org.signal.core.ui.logging.LoggingFragment;
import com.red.sovereign.R;
import com.red.sovereign.components.ContactFilterView;
import com.red.sovereign.contacts.ContactSelectionDisplayMode;
import com.red.sovereign.contacts.paged.ChatType;
import com.red.sovereign.contacts.selection.ContactSelectionArguments;
import com.red.sovereign.conversation.ConversationIntents;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.payments.CanNotSendPaymentDialog;
import com.red.sovereign.payments.preferences.model.PayeeParcelable;
import com.red.sovereign.recipients.Recipient;
import com.red.sovereign.recipients.RecipientId;
import com.red.sovereign.util.SystemWindowInsetsSetter;
import com.red.sovereign.util.ViewUtil;
import com.red.sovereign.util.navigation.SafeNavigation;
import org.signal.core.util.ExpiringProfileCredentialUtil;

import java.util.Optional;
import java.util.function.Consumer;


public class PaymentRecipientSelectionFragment extends LoggingFragment implements ContactSelectionListFragment.OnContactSelectedListener {

  private Toolbar                      toolbar;
  private ContactFilterView            contactFilterView;
  private ContactSelectionListFragment contactsFragment;

  public PaymentRecipientSelectionFragment() {
    super(R.layout.payment_recipient_selection_fragment);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    toolbar = view.findViewById(R.id.payment_recipient_selection_fragment_toolbar);
    toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(v).popBackStack());

    toolbar.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
    SystemWindowInsetsSetter.attach(toolbar, getViewLifecycleOwner(), WindowInsetsCompat.Type.statusBars());

    contactFilterView = view.findViewById(R.id.contact_filter_edit_text);

    Bundle arguments = new Bundle();
    arguments.putBoolean(ContactSelectionArguments.REFRESHABLE, false);
    arguments.putInt(ContactSelectionArguments.DISPLAY_MODE, ContactSelectionDisplayMode.FLAG_PUSH | ContactSelectionDisplayMode.FLAG_HIDE_NEW);
    arguments.putBoolean(ContactSelectionArguments.CAN_SELECT_SELF, false);

    Fragment child = getChildFragmentManager().findFragmentById(R.id.contact_selection_list_fragment_holder);
    if (child == null) {
      FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
      contactsFragment = new ContactSelectionListFragment();
      contactsFragment.setArguments(arguments);
      transaction.add(R.id.contact_selection_list_fragment_holder, contactsFragment);
      transaction.commit();
    } else {
      contactsFragment = (ContactSelectionListFragment) child;
    }

    initializeSearch();
  }

  private void initializeSearch() {
    contactFilterView.setOnFilterChangedListener(filter -> contactsFragment.setQueryFilter(filter));
  }

  @Override
  public void onBeforeContactSelected(boolean isFromUnknownSearchKey, @NonNull Optional<RecipientId> recipientId, @Nullable String number, @NonNull Optional<ChatType> chatType, @NonNull Consumer<Boolean> callback) {
    if (recipientId.isPresent()) {
      SimpleTask.run(getViewLifecycleOwner().getLifecycle(),
                     () -> Recipient.resolved(recipientId.get()),
                     this::createPaymentOrShowWarningDialog);
    }

    callback.accept(false);
  }

  @Override
  public void onContactDeselected(@NonNull Optional<RecipientId> recipientId, @Nullable String number, @NonNull Optional<ChatType> chatType) {}

  @Override
  public void onSelectionChanged() {
  }

  private void hideKeyboard() {
    ViewUtil.hideKeyboard(requireContext(), toolbar);
    toolbar.clearFocus();
  }

  private void createPaymentOrShowWarningDialog(@NonNull Recipient recipient) {
    if (ExpiringProfileCredentialUtil.isValid(recipient.getExpiringProfileKeyCredential())) {
      createPayment(recipient.getId());
    } else {
      showWarningDialog(recipient.getId());
    }
  }

  private void createPayment(@NonNull RecipientId recipientId) {
    hideKeyboard();
    SafeNavigation.safeNavigate(Navigation.findNavController(requireView()), PaymentRecipientSelectionFragmentDirections.actionPaymentRecipientSelectionToCreatePayment(new PayeeParcelable(recipientId)));
  }

  private void showWarningDialog(@NonNull RecipientId recipientId) {
    CanNotSendPaymentDialog.show(requireContext(),
                                 () -> openConversation(recipientId));
  }

  private void openConversation(@NonNull RecipientId recipientId) {
    SimpleTask.run(getViewLifecycleOwner().getLifecycle(),
                   () -> REDDatabase.threads().getOrCreateThreadIdFor(Recipient.resolved(recipientId)),
                   threadId -> startActivity(ConversationIntents.createBuilderSync(requireContext(), recipientId, threadId).build()));
  }
}
