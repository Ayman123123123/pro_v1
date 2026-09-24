package com.red.sovereign.reactions.any;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.stream.Collectors;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.concurrent.REDExecutors;
import org.signal.core.util.logging.Log;
import com.red.sovereign.R;
import com.red.sovereign.components.emoji.RecentEmojiPageModel;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.database.model.MessageId;
import com.red.sovereign.database.model.ReactionRecord;
import com.red.sovereign.emoji.EmojiCategory;
import com.red.sovereign.emoji.EmojiSource;
import com.red.sovereign.reactions.ReactionDetails;
import com.red.sovereign.recipients.Recipient;
import com.red.sovereign.sms.MessageSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

final class ReactWithAnyEmojiRepository {

  private static final String TAG = Log.tag(ReactWithAnyEmojiRepository.class);

  private final Context                     context;
  private final RecentEmojiPageModel        recentEmojiPageModel;
  private final List<ReactWithAnyEmojiPage> emojiPages;

  ReactWithAnyEmojiRepository(@NonNull Context context, @NonNull String storageKey) {
    this.context              = context;
    this.recentEmojiPageModel = new RecentEmojiPageModel(context, storageKey);
    this.emojiPages           = new LinkedList<>();

    emojiPages.addAll(EmojiSource.getLatest().getDisplayPages().stream()
                                 .filter(p -> p.getIconAttr() != EmojiCategory.EMOTICONS.getIcon())
                                 .map(page -> new ReactWithAnyEmojiPage(Collections.singletonList(new ReactWithAnyEmojiPageBlock(EmojiCategory.getCategoryLabel(page.getIconAttr()), page))))
                                 .collect(Collectors.toList()));
  }

  List<ReactWithAnyEmojiPage> getEmojiPageModels(@NonNull List<ReactionDetails> thisMessagesReactions) {
    List<ReactWithAnyEmojiPage> pages       = new LinkedList<>();
    List<String>                thisMessage = thisMessagesReactions.stream()
                                                                   .map(ReactionDetails::getDisplayEmoji)
                                                                   .distinct().collect(Collectors.toList());

    if (thisMessage.isEmpty()) {
      pages.add(new ReactWithAnyEmojiPage(Collections.singletonList(new ReactWithAnyEmojiPageBlock(R.string.ReactWithAnyEmojiBottomSheetDialogFragment__recently_used, recentEmojiPageModel))));
    } else {
      pages.add(new ReactWithAnyEmojiPage(Arrays.asList(new ReactWithAnyEmojiPageBlock(R.string.ReactWithAnyEmojiBottomSheetDialogFragment__this_message, new ThisMessageEmojiPageModel(thisMessage)),
                                                        new ReactWithAnyEmojiPageBlock(R.string.ReactWithAnyEmojiBottomSheetDialogFragment__recently_used, recentEmojiPageModel))));
    }

    pages.addAll(emojiPages);

    return pages;
  }

  void addEmojiToMessage(@NonNull String emoji, @NonNull MessageId messageId) {
    REDExecutors.BOUNDED.execute(() -> {
      ReactionRecord  oldRecord = REDDatabase.reactions().getReactions(messageId).stream()
                                                .filter(record -> record.getAuthor().equals(Recipient.self().getId()))
                                                .findFirst()
                                                .orElse(null);

      if (oldRecord != null && oldRecord.getEmoji().equals(emoji)) {
        MessageSender.sendReactionRemoval(context, messageId, oldRecord);
      } else {
        MessageSender.sendNewReaction(context, messageId, emoji);
        ThreadUtil.runOnMain(() -> recentEmojiPageModel.onCodePointSelected(emoji));
      }
    });
  }
}
