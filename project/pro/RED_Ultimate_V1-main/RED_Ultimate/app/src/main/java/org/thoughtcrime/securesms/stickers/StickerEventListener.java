package com.red.sovereign.stickers;

import androidx.annotation.NonNull;

import com.red.sovereign.database.model.StickerRecord;

public interface StickerEventListener {
  void onStickerSelected(@NonNull StickerRecord sticker);

  void onStickerManagementClicked();
}
