package com.red.sovereign.scribbles;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.signal.core.util.concurrent.REDExecutors;
import com.red.sovereign.R;
import com.red.sovereign.components.emoji.MediaKeyboard;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.database.model.StickerRecord;
import com.red.sovereign.keyboard.KeyboardPage;
import com.red.sovereign.keyboard.sticker.StickerKeyboardPageFragment;
import com.red.sovereign.keyboard.sticker.StickerSearchDialogFragment;
import com.red.sovereign.scribbles.stickers.FeatureSticker;
import com.red.sovereign.scribbles.stickers.ScribbleStickersFragment;
import com.red.sovereign.stickers.StickerEventListener;
import com.red.sovereign.stickers.manage.StickerManagementScreen;
import com.red.sovereign.util.ViewUtil;

public final class ImageEditorStickerSelectActivity extends AppCompatActivity implements StickerEventListener, MediaKeyboard.MediaKeyboardListener, StickerKeyboardPageFragment.Callback, ScribbleStickersFragment.Callback {

  public static final String EXTRA_FEATURE_STICKER = "imageEditor.featureSticker";

  @Override
  protected void attachBaseContext(@NonNull Context newBase) {
    getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    super.attachBaseContext(newBase);
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    EdgeToEdge.enable(this);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.scribble_select_new_sticker_activity);

    ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (view, insets) -> {
      Insets safeArea = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
      view.setPadding(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom);
      return insets;
    });
  }

  @Override
  public void onShown() {
  }

  @Override
  public void onHidden() {
    finish();
  }

  @Override
  public void onKeyboardChanged(@NonNull KeyboardPage page) {
  }

  @Override
  public void onStickerSelected(@NonNull StickerRecord sticker) {
    Intent intent = new Intent();
    intent.setData(sticker.uri);
    setResult(RESULT_OK, intent);

    REDExecutors.BOUNDED.execute(() -> REDDatabase.stickers().updateStickerLastUsedTime(sticker.rowId, System.currentTimeMillis()));
    ViewUtil.hideKeyboard(this, findViewById(android.R.id.content));
    finish();
  }

  @Override
  public void onStickerManagementClicked() {
    StickerManagementScreen.show(this);
  }

  @Override
  public void openStickerSearch() {
    StickerSearchDialogFragment.show(getSupportFragmentManager());
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      onBackPressed();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onFeatureSticker(FeatureSticker featureSticker) {
    Intent intent = new Intent();
    intent.putExtra(EXTRA_FEATURE_STICKER, featureSticker.getType());
    setResult(RESULT_OK, intent);

    ViewUtil.hideKeyboard(this, findViewById(android.R.id.content));
    finish();
  }
}
