package com.red.sovereign.wallpaper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import org.signal.core.util.concurrent.REDExecutors;
import com.red.sovereign.conversation.colors.ChatColors;
import com.red.sovereign.conversation.colors.ChatColorsPalette;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.keyvalue.REDStore;
import com.red.sovereign.recipients.Recipient;
import com.red.sovereign.recipients.RecipientId;
import org.signal.core.util.concurrent.SerialExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

class ChatWallpaperRepository {

  private static final Executor EXECUTOR = new SerialExecutor(REDExecutors.BOUNDED);

  @MainThread
  @Nullable ChatWallpaper getCurrentWallpaper(@Nullable RecipientId recipientId) {
    if (recipientId != null) {
      return Recipient.live(recipientId).get().getWallpaper();
    } else {
      return REDStore.wallpaper().getWallpaper();
    }
  }

  @MainThread
  @NonNull ChatColors getCurrentChatColors(@Nullable RecipientId recipientId) {
    if (recipientId != null) {
      return Recipient.live(recipientId).get().getChatColors();
    } else if (REDStore.chatColors().hasChatColors()) {
      return Objects.requireNonNull(REDStore.chatColors().getChatColors());
    } else if (REDStore.wallpaper().hasWallpaperSet()) {
      return Objects.requireNonNull(REDStore.wallpaper().getWallpaper()).getAutoChatColors();
    } else {
      return ChatColorsPalette.Bubbles.getDefault().withId(ChatColors.Id.Auto.INSTANCE);
    }
  }

  void getAllWallpaper(@NonNull Consumer<List<ChatWallpaper>> consumer) {
    EXECUTOR.execute(() -> {
      List<ChatWallpaper> wallpapers = new ArrayList<>(ChatWallpaper.BuiltIns.INSTANCE.getAllBuiltIns());

      wallpapers.addAll(WallpaperStorage.getAll());
      consumer.accept(wallpapers);
    });
  }

  void saveWallpaper(@Nullable RecipientId recipientId, @Nullable ChatWallpaper chatWallpaper, @NonNull Runnable onWallpaperSaved) {
    EXECUTOR.execute(() -> {
      if (recipientId != null) {
        //noinspection CodeBlock2Expr
        REDDatabase.recipients().setWallpaper(recipientId, chatWallpaper, true);
        onWallpaperSaved.run();
      } else {
        REDStore.wallpaper().setWallpaper(chatWallpaper);
        onWallpaperSaved.run();
      }
    });
  }

  void resetAllWallpaper(@NonNull Runnable onWallpaperReset) {
    EXECUTOR.execute(() -> {
      REDStore.wallpaper().setWallpaper(null);
      REDDatabase.recipients().resetAllWallpaper();
      onWallpaperReset.run();
    });
  }

  void resetAllChatColors(@NonNull Runnable onColorsReset) {
    REDStore.chatColors().setChatColors(null);
    EXECUTOR.execute(() -> {
      REDDatabase.recipients().clearAllColors();
      onColorsReset.run();
    });
  }

  void setDimInDarkTheme(@Nullable RecipientId recipientId, boolean dimInDarkTheme) {
    if (recipientId != null) {
      EXECUTOR.execute(() -> {
        Recipient recipient = Recipient.resolved(recipientId);
        if (recipient.getHasOwnWallpaper()) {
          REDDatabase.recipients().setDimWallpaperInDarkTheme(recipientId, dimInDarkTheme);
        } else if (recipient.getHasWallpaper()) {
          REDDatabase.recipients()
                       .setWallpaper(recipientId,
                                     ChatWallpaperFactory.updateWithDimming(recipient.getWallpaper(),
                                                                            dimInDarkTheme ? ChatWallpaper.FIXED_DIM_LEVEL_FOR_DARK_THEME
                                                                                           : 0f),
                                     false);
        } else {
          throw new IllegalStateException("Unexpected call to setDimInDarkTheme, no wallpaper has been set on the given recipient or globally.");
        }
      });
    } else {
      REDStore.wallpaper().setDimInDarkTheme(dimInDarkTheme);
    }
  }

  public void clearChatColor(@Nullable RecipientId recipientId, @NonNull Runnable onChatColorCleared) {
    if (recipientId == null) {
      REDStore.chatColors().setChatColors(null);
      onChatColorCleared.run();
    } else {
      EXECUTOR.execute(() -> {
        REDDatabase.recipients().clearColor(recipientId);
        onChatColorCleared.run();
      });
    }
  }
}
