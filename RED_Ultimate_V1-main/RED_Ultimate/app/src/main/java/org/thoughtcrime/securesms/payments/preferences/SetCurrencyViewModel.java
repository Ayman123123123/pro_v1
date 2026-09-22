package com.red.sovereign.payments.preferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.signal.core.util.logging.Log;
import com.red.sovereign.BuildConfig;
import com.red.sovereign.R;
import com.red.sovereign.components.settings.SettingHeader;
import com.red.sovereign.components.settings.SettingProgress;
import com.red.sovereign.components.settings.SingleSelectSetting;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.keyvalue.REDStore;
import com.red.sovereign.payments.currency.CurrencyExchange;
import com.red.sovereign.payments.currency.CurrencyExchangeRepository;
import com.red.sovereign.payments.currency.CurrencyUtil;
import com.red.sovereign.util.AsynchronousCallback;
import org.signal.core.util.SetUtil;
import com.red.sovereign.util.adapter.mapping.MappingModelList;
import com.red.sovereign.util.livedata.Store;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import kotlin.Pair;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

public final class SetCurrencyViewModel extends ViewModel {

  private static final String TAG = Log.tag(SetCurrencyViewModel.class);

  private final Store<SetCurrencyState>     store;
  private final LiveData<CurrencyListState> list;

  public SetCurrencyViewModel(@NonNull CurrencyExchangeRepository currencyExchangeRepository) {
    this.store = new Store<>(new SetCurrencyState(REDStore.payments().currentCurrency()));
    this.list  = Transformations.map(this.store.getStateLiveData(), this::createListState);

    this.store.update(REDStore.payments().liveCurrentCurrency(), (currency, state) -> state.updateCurrentCurrency(currency));

    currencyExchangeRepository.getCurrencyExchange(new AsynchronousCallback.WorkerThread<CurrencyExchange, Throwable>() {
      @Override
      public void onComplete(@Nullable CurrencyExchange result) {
        store.update(state -> state.updateCurrencyExchange(Objects.requireNonNull(result)));
      }

      @Override
      public void onError(@Nullable Throwable error) {
        Log.w(TAG, error);
        store.update(state -> state.updateExchangeRateLoadState(LoadState.ERROR));
      }
    }, false);
  }

  public void select(@NonNull Currency selection) {
    REDStore.payments().setCurrentCurrency(selection);
  }

  public LiveData<CurrencyListState> getCurrencyListState() {
    return list;
  }

  private @NonNull CurrencyListState createListState(SetCurrencyState state) {
    MappingModelList items                  = new MappingModelList();
    boolean          areAllCurrenciesLoaded = state.getCurrencyExchangeLoadState() == LoadState.LOADED;

    items.addAll(fromCurrencies(state.getDefaultCurrencies(), state.getCurrentCurrency()));
    items.add(new SettingHeader.Item(R.string.SetCurrencyFragment__all_currencies));
    if (areAllCurrenciesLoaded) {
      items.addAll(fromCurrencies(state.getOtherCurrencies(), state.getCurrentCurrency()));
    } else {
      items.add(new SettingProgress.Item());
    }

    return new CurrencyListState(items, findSelectedIndex(items), areAllCurrenciesLoaded);
  }

  private @NonNull MappingModelList fromCurrencies(@NonNull Collection<Currency> currencies, @NonNull Currency currentCurrency) {
    return currencies.stream()
                 .map(c -> new SingleSelectSetting.Item(c, c.getDisplayName(Locale.getDefault()), c.getCurrencyCode(), c.equals(currentCurrency)))
                 .sorted(Comparator.comparing(SingleSelectSetting.Item::getText))
                 .collect(MappingModelList.toMappingModelList());
  }

  private int findSelectedIndex(MappingModelList items) {
    for (int i=0; i<items.size(); i++) {
      if (items.get(i) instanceof SingleSelectSetting.Item model && model.isSelected()) {
        return i;
      }
    }
    return -1;
  }

  public static class CurrencyListState {
    private final MappingModelList items;
    private final int              selectedIndex;
    private final boolean          isLoaded;

    public CurrencyListState(@NonNull MappingModelList items, int selectedIndex, boolean isLoaded) {
      this.items         = items;
      this.isLoaded      = isLoaded;
      this.selectedIndex = selectedIndex;
    }

    public boolean isLoaded() {
      return isLoaded;
    }

    public @NonNull MappingModelList getItems() {
      return items;
    }

    public int getSelectedIndex() {
      return selectedIndex;
    }
  }

  public static class SetCurrencyState {
    private static final List<Currency> DEFAULT_CURRENCIES = Arrays.stream(BuildConfig.DEFAULT_CURRENCIES.split(","))
                                                                   .map(CurrencyUtil::getCurrencyByCurrencyCode)
                                                                   .filter(Objects::nonNull)
                                                                   .collect(Collectors.toList());

    private final Currency             currentCurrency;
    private final CurrencyExchange     currencyExchange;
    private final LoadState            currencyExchangeLoadState;
    private final Collection<Currency> defaultCurrencies;
    private final Collection<Currency> otherCurrencies;

    public SetCurrencyState(@NonNull Currency currentCurrency) {
      this(currentCurrency, new CurrencyExchange(emptyMap(), 0), LoadState.LOADING, DEFAULT_CURRENCIES, emptyList());
    }

    public SetCurrencyState(@NonNull Currency currentCurrency,
                            @NonNull CurrencyExchange currencyExchange,
                            @NonNull LoadState loadState,
                            @NonNull Collection<Currency> defaultCurrencies,
                            @NonNull Collection<Currency> otherCurrencies)
    {
      this.currentCurrency           = currentCurrency;
      this.currencyExchange          = currencyExchange;
      this.currencyExchangeLoadState = loadState;
      this.defaultCurrencies         = defaultCurrencies;
      this.otherCurrencies           = otherCurrencies;
    }

    public @NonNull Currency getCurrentCurrency() {
      return currentCurrency;
    }

    public @NonNull LoadState getCurrencyExchangeLoadState() {
      return currencyExchangeLoadState;
    }

    public @NonNull Collection<Currency> getDefaultCurrencies() {
      return defaultCurrencies;
    }

    public @NonNull Collection<Currency> getOtherCurrencies() {
      return otherCurrencies;
    }

    public @NonNull SetCurrencyState updateExchangeRateLoadState(@NonNull LoadState currencyExchangeLoadState) {
      return new SetCurrencyState(this.currentCurrency,
                                  this.currencyExchange,
                                  currencyExchangeLoadState,
                                  this.defaultCurrencies,
                                  this.otherCurrencies);
    }

    public @NonNull SetCurrencyState updateCurrencyExchange(@NonNull CurrencyExchange currencyExchange) {
      List<Currency> currencies = currencyExchange.getSupportedCurrencies();

      Collection<Currency> defaultCurrencies = SetUtil.intersection(currencies, DEFAULT_CURRENCIES);
      Collection<Currency> otherCurrencies   = SetUtil.difference(currencies, defaultCurrencies);

      return new SetCurrencyState(this.currentCurrency,
                                  currencyExchange,
                                  LoadState.LOADED,
                                  defaultCurrencies,
                                  otherCurrencies);
    }

    public @NonNull SetCurrencyState updateCurrentCurrency(@NonNull Currency currentCurrency) {
      return new SetCurrencyState(currentCurrency,
                                  this.currencyExchange,
                                  this.currencyExchangeLoadState,
                                  this.defaultCurrencies,
                                  this.otherCurrencies);
    }
  }

  public static final class Factory implements ViewModelProvider.Factory {
    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new SetCurrencyViewModel(new CurrencyExchangeRepository(AppDependencies.getPayments())));
    }
  }
}
