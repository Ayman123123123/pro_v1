package com.red.sovereign.reactions.any;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.red.sovereign.components.emoji.EmojiPageModel;
import com.red.sovereign.components.emoji.EmojiPageViewGridAdapter;
import com.red.sovereign.components.emoji.RecentEmojiPageModel;
import com.red.sovereign.database.model.MessageId;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.emoji.EmojiCategory;
import com.red.sovereign.keyboard.emoji.EmojiCategoryMappingModel;
import com.red.sovereign.keyboard.emoji.RecentsMappingModel;
import com.red.sovereign.keyboard.emoji.search.EmojiSearchRepository;
import com.red.sovereign.keyvalue.REDStore;
import com.red.sovereign.reactions.ReactionsRepository;
import com.red.sovereign.util.TextSecurePreferences;
import com.red.sovereign.util.adapter.mapping.MappingModelList;

import java.util.List;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

public final class ReactWithAnyEmojiViewModel extends ViewModel {

  private static final int SEARCH_LIMIT = 40;

  private final ReactWithAnyEmojiRepository repository;
  private final long                        messageId;
  private final boolean                     isMms;
  private final EmojiSearchRepository       emojiSearchRepository;

  private final Observable<MappingModelList>       categories;
  private final Observable<MappingModelList>       emojiList;
  private final BehaviorSubject<EmojiSearchResult> searchResults;
  private final BehaviorSubject<String>            selectedKey;

  private ReactWithAnyEmojiViewModel(
      @NonNull ReactionsRepository reactionsRepository,
      @NonNull ReactWithAnyEmojiRepository repository,
      long messageId,
      boolean isMms,
      @NonNull EmojiSearchRepository emojiSearchRepository)
  {
    this.repository            = repository;
    this.messageId             = messageId;
    this.isMms                 = isMms;
    this.emojiSearchRepository = emojiSearchRepository;
    this.searchResults         = BehaviorSubject.createDefault(new EmojiSearchResult());
    this.selectedKey           = BehaviorSubject.createDefault(getStartingKey());

    Observable<List<ReactWithAnyEmojiPage>> emojiPages = reactionsRepository.getReactions(new MessageId(messageId))
                                                                            .map(repository::getEmojiPageModels);

    Observable<MappingModelList> emojiList = emojiPages.map(pages -> {
      MappingModelList list = new MappingModelList();

      for (ReactWithAnyEmojiPage page : pages) {
        String key = page.getKey();
        for (ReactWithAnyEmojiPageBlock block : page.getPageBlocks()) {
          list.add(new EmojiPageViewGridAdapter.EmojiHeader(key, block.getLabel()));
          list.addAll(toMappingModels(block.getPageModel()));
        }
      }

      return list;
    });

    this.categories = Observable.combineLatest(emojiPages, this.selectedKey.distinctUntilChanged(), (pages, selectedKey) -> {
      MappingModelList list = new MappingModelList();
      list.add(new RecentsMappingModel(RecentEmojiPageModel.KEY.equals(selectedKey)));
      list.addAll(pages.stream()
                       .filter(p -> !RecentEmojiPageModel.KEY.equals(p.getKey()))
                       .map(p -> {
                         EmojiCategory category = EmojiCategory.forKey(p.getKey());
                         return new EmojiCategoryMappingModel(category, category.getKey().equals(selectedKey));
                       })
                       .collect(Collectors.toList()));
      return list;
    });

    this.emojiList = Observable.combineLatest(emojiList, searchResults.distinctUntilChanged(), (all, search) -> {
      if (TextUtils.isEmpty(search.query)) {
        return all;
      } else {
        if (search.model.getDisplayEmoji().isEmpty()) {
          return MappingModelList.singleton(new EmojiPageViewGridAdapter.EmojiNoResultsModel());
        }
        return toMappingModels(search.model);
      }
    });
  }

  @NonNull Observable<MappingModelList> getCategories() {
    return categories.observeOn(AndroidSchedulers.mainThread());
  }

  @NonNull Observable<String> getSelectedKey() {
    return selectedKey.observeOn(AndroidSchedulers.mainThread());
  }

  @NonNull Observable<MappingModelList> getEmojiList() {
    return emojiList.observeOn(AndroidSchedulers.mainThread());
  }

  void onEmojiSelected(@NonNull String emoji) {
    if (messageId > 0) {
      REDStore.emoji().setPreferredVariation(emoji);
      repository.addEmojiToMessage(emoji, new MessageId(messageId));
    }
  }

  public void onQueryChanged(String query) {
    emojiSearchRepository.submitQuery(query, false, SEARCH_LIMIT, m -> searchResults.onNext(new EmojiSearchResult(query, m)));
  }

  public void selectPage(@NonNull String key) {
    selectedKey.onNext(key);
  }

  private static @NonNull MappingModelList toMappingModels(@NonNull EmojiPageModel model) {
    return model.getDisplayEmoji()
                .stream()
                .map(e -> new EmojiPageViewGridAdapter.EmojiModel(model.getKey(), e))
                .collect(MappingModelList.collect());
  }

  private static @NonNull String getStartingKey() {
    if (RecentEmojiPageModel.hasRecents(AppDependencies.getApplication(), TextSecurePreferences.RECENT_STORAGE_KEY)) {
      return RecentEmojiPageModel.KEY;
    } else {
      return EmojiCategory.PEOPLE.getKey();
    }
  }

  private static class EmojiSearchResult {
    private final String         query;
    private final EmojiPageModel model;

    private EmojiSearchResult(@NonNull String query, @Nullable EmojiPageModel model) {
      this.query = query;
      this.model = model;
    }

    public EmojiSearchResult() {
      this("", null);
    }
  }

  static class Factory implements ViewModelProvider.Factory {

    private final ReactionsRepository         reactionsRepository;
    private final ReactWithAnyEmojiRepository repository;
    private final long                        messageId;
    private final boolean                     isMms;

    Factory(@NonNull ReactionsRepository reactionsRepository, @NonNull ReactWithAnyEmojiRepository repository, long messageId, boolean isMms) {
      this.reactionsRepository = reactionsRepository;
      this.repository          = repository;
      this.messageId           = messageId;
      this.isMms               = isMms;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new ReactWithAnyEmojiViewModel(reactionsRepository, repository, messageId, isMms, new EmojiSearchRepository(AppDependencies.getApplication())));
    }
  }

}
