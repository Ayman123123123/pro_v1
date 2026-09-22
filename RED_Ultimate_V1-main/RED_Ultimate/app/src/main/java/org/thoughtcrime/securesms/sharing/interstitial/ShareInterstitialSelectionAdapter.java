package com.red.sovereign.sharing.interstitial;

import com.red.sovereign.R;
import com.red.sovereign.util.adapter.mapping.MappingAdapter;
import com.red.sovereign.util.viewholders.RecipientViewHolder;

class ShareInterstitialSelectionAdapter extends MappingAdapter {
  ShareInterstitialSelectionAdapter() {
    registerFactory(ShareInterstitialMappingModel.class, RecipientViewHolder.createFactory(R.layout.share_contact_selection_item, null));
  }
}
