package com.red.sovereign.stories.landing

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.coroutines.launch
import org.signal.core.ui.permissions.Permissions
import org.signal.core.ui.view.Stub
import org.signal.core.util.concurrent.LifecycleDisposable
import com.red.sovereign.MainActivity
import com.red.sovereign.R
import com.red.sovereign.banner.BannerManager
import com.red.sovereign.banner.banners.DeprecatedBuildBanner
import com.red.sovereign.banner.banners.UnauthorizedBanner
import com.red.sovereign.components.settings.DSLConfiguration
import com.red.sovereign.components.settings.DSLSettingsFragment
import com.red.sovereign.components.settings.DSLSettingsText
import com.red.sovereign.components.settings.configure
import com.red.sovereign.components.snackbars.SnackbarState
import com.red.sovereign.conversation.ConversationIntents
import com.red.sovereign.conversation.mutiselect.forward.MultiselectForwardFragment
import com.red.sovereign.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import com.red.sovereign.database.model.MmsMessageRecord
import com.red.sovereign.database.model.StoryViewState
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.main.MainNavigationDetailLocation
import com.red.sovereign.main.MainNavigationListLocation
import com.red.sovereign.main.MainNavigationViewModel
import com.red.sovereign.main.MainSnackbarHostKey
import com.red.sovereign.main.MainToolbarMode
import com.red.sovereign.main.MainToolbarViewModel
import com.red.sovereign.main.Material3OnScrollHelperBinder
import com.red.sovereign.safety.SafetyNumberBottomSheet
import com.red.sovereign.stories.StoryTextPostModel
import com.red.sovereign.stories.StoryViewerArgs
import com.red.sovereign.stories.dialogs.StoryContextMenu
import com.red.sovereign.stories.dialogs.StoryDialogs
import com.red.sovereign.stories.viewer.StoryViewerActivity
import com.red.sovereign.util.ViewUtil
import com.red.sovereign.util.adapter.mapping.MappingAdapter
import com.red.sovereign.util.fragments.requireListener
import com.red.sovereign.util.visible

/**
 * The "landing page" for Stories.
 */
class StoriesLandingFragment : DSLSettingsFragment(layoutId = R.layout.stories_landing_fragment) {

  companion object {
    private const val LIST_SMOOTH_SCROLL_TO_TOP_THRESHOLD = 25
  }

  private lateinit var emptyNotice: View

  private lateinit var bannerView: Stub<ComposeView>

  private val lifecycleDisposable = LifecycleDisposable()

  private val viewModel: StoriesLandingViewModel by activityViewModels(
    factoryProducer = {
      StoriesLandingViewModel.Factory(StoriesLandingRepository(requireContext()))
    }
  )

  private val mainToolbarViewModel: MainToolbarViewModel by activityViewModels()
  private val mainNavigationViewModel: MainNavigationViewModel by activityViewModels()

  private lateinit var adapter: MappingAdapter

  override fun onResume() {
    super.onResume()
    viewModel.isTransitioningToAnotherScreen = false
    initializeSearchAction()
    viewModel.markStoriesRead()

    AppDependencies.expireStoriesManager.scheduleIfNecessary()
  }

  private fun initializeSearchAction() {
    lifecycleDisposable += mainToolbarViewModel.getSearchEventsFlowable().subscribeBy {
      when (it) {
        MainToolbarViewModel.Event.Search.Close -> {
          viewModel.setSearchQuery("")
        }
        MainToolbarViewModel.Event.Search.Open -> {
          mainToolbarViewModel.setSearchHint(R.string.SearchToolbar_search)
        }
        is MainToolbarViewModel.Event.Search.Query -> {
          viewModel.setSearchQuery(it.query.trim())
        }
      }
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    bannerView = ViewUtil.findStubById(view, R.id.banner_stub)
    initializeBanners()
  }

  private fun initializeBanners() {
    val bannerManager = BannerManager(
      banners = listOf(
        DeprecatedBuildBanner(),
        UnauthorizedBanner(requireContext())
      ),
      onNewBannerShownListener = {
        if (bannerView.resolved()) {
          bannerView.get().addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            recyclerView?.setPadding(0, bottom - top, 0, 0)
          }
          recyclerView?.clipToPadding = false
        }
      },
      onNoBannerShownListener = {
        recyclerView?.clipToPadding = true
      }
    )
    bannerManager.updateContent(bannerView.get())
  }

  override fun bindAdapter(adapter: MappingAdapter) {
    this.adapter = adapter

    StoriesLandingItem.register(adapter)
    MyStoriesItem.register(adapter)
    ExpandHeader.register(adapter)

    requireListener<Material3OnScrollHelperBinder>().bindScrollHelper(recyclerView!!, viewLifecycleOwner)

    lifecycleDisposable.bindTo(viewLifecycleOwner)
    emptyNotice = requireView().findViewById(R.id.empty_notice)

    viewModel.state.observe(viewLifecycleOwner) {
      if (it.loadingState == StoriesLandingState.LoadingState.LOADED) {
        adapter.submitList(getConfiguration(it).toMappingModelList())
        emptyNotice.visible = it.hasNoStories
      }
    }

    requireActivity().onBackPressedDispatcher.addCallback(
      viewLifecycleOwner,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          if (!closeSearchIfOpen()) {
            if (mainNavigationViewModel.storiesBackStackEntries.last() != MainNavigationDetailLocation.Empty) {
              mainNavigationViewModel.popStoriesDetailLocation()
            } else {
              mainNavigationViewModel.onChatsSelected()
            }
          }
        }
      }
    )

    lifecycleDisposable += mainNavigationViewModel.tabClickEventsObservable
      .filter { it == MainNavigationListLocation.STORIES }
      .subscribeBy(onNext = {
        val layoutManager = recyclerView?.layoutManager as? LinearLayoutManager ?: return@subscribeBy
        if (layoutManager.findFirstVisibleItemPosition() <= LIST_SMOOTH_SCROLL_TO_TOP_THRESHOLD) {
          recyclerView?.smoothScrollToPosition(0)
        } else {
          recyclerView?.scrollToPosition(0)
        }
      })

    this.adapter.registerAdapterDataObserver(object : AdapterDataObserver() {
      override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
        (requireActivity() as? MainActivity)?.onFirstRender()
        this@StoriesLandingFragment.adapter.unregisterAdapterDataObserver(this)
      }
    })
  }

  private fun getConfiguration(state: StoriesLandingState): DSLConfiguration {
    return configure {
      val (stories, hidden) = state.storiesLandingItems.filter {
        if (state.searchQuery.isNotEmpty()) {
          val storyRecipientName = it.storyRecipient.getDisplayName(requireContext())
          val individualRecipientName = it.individualRecipient.getDisplayName(requireContext())

          storyRecipientName.contains(state.searchQuery, ignoreCase = true) || individualRecipientName.contains(state.searchQuery, ignoreCase = true)
        } else {
          true
        }
      }.map {
        createStoryLandingItem(it)
      }.partition {
        !it.data.isHidden
      }

      if (state.displayMyStoryItem) {
        customPref(
          MyStoriesItem.Model(
            lifecycleOwner = viewLifecycleOwner,
            onClick = {
              mainNavigationViewModel.goToCameraFirstStoryCapture()
            }
          )
        )
      }

      stories.forEach { item ->
        customPref(item)
      }

      if (hidden.isNotEmpty()) {
        customPref(
          ExpandHeader.Model(
            title = DSLSettingsText.from(R.string.StoriesLandingFragment__hidden_stories),
            isExpanded = state.isHiddenContentVisible,
            onClick = { viewModel.setHiddenContentVisible(it) }
          )
        )
      }

      if (state.isHiddenContentVisible) {
        hidden.forEach { item ->
          customPref(item)
        }
      }
    }
  }

  private fun createStoryLandingItem(data: StoriesLandingItemData): StoriesLandingItem.Model {
    return StoriesLandingItem.Model(
      data = data,
      onRowClick = { model, preview ->
        openStoryViewer(model, preview, false)
      },
      onForwardStory = {
        MultiselectForwardFragmentArgs.create(requireContext(), it.data.primaryStory.multiselectCollection.toSet()) { args ->
          MultiselectForwardFragment.showBottomSheet(childFragmentManager, args)
        }
      },
      onGoToChat = { model ->
        lifecycleDisposable += ConversationIntents.createBuilder(requireContext(), model.data.storyRecipient.id, -1L)
          .subscribeBy {
            startActivityIfAble(it.build())
          }
      },
      onHideStory = {
        if (!it.data.isHidden) {
          handleHideStory(it)
        } else {
          lifecycleDisposable += viewModel.setHideStory(it.data.storyRecipient, !it.data.isHidden).subscribe()
        }
      },
      onShareStory = {
        StoryContextMenu.share(this@StoriesLandingFragment, it.data.primaryStory.messageRecord as MmsMessageRecord)
      },
      onSave = {
        lifecycleScope.launch {
          StoryContextMenu.save(
            fragment = this@StoriesLandingFragment,
            messageRecord = it.data.primaryStory.messageRecord
          )
        }
      },
      onDeleteStory = {
        handleDeleteStory(it)
      },
      onInfo = { model, preview ->
        openStoryViewer(model, preview, true)
      },
      onAvatarClick = {
        mainNavigationViewModel.goToCameraFirstStoryCapture()
      },
      onLockList = {
        recyclerView?.suppressLayout(true)
      },
      onUnlockList = {
        recyclerView?.suppressLayout(false)
      }
    )
  }

  private fun openStoryViewer(model: StoriesLandingItem.Model, preview: View, isFromInfoContextMenuAction: Boolean) {
    if (model.data.storyRecipient.isMyStory) {
      mainNavigationViewModel.goTo(MainNavigationDetailLocation.Stories.MyStories)
    } else if (model.data.primaryStory.messageRecord.isOutgoing && model.data.primaryStory.messageRecord.isFailed) {
      if (model.data.primaryStory.messageRecord.isIdentityMismatchFailure) {
        SafetyNumberBottomSheet
          .forOutgoingMessageRecord(requireContext(), model.data.primaryStory.messageRecord)
          .show(childFragmentManager)
      } else {
        StoryDialogs.resendStory(requireContext()) {
          lifecycleDisposable += viewModel.resend(model.data.primaryStory.messageRecord).subscribe()
        }
      }
    } else {
      val options = ActivityOptionsCompat.makeSceneTransitionAnimation(requireActivity(), preview, ViewCompat.getTransitionName(preview) ?: "")

      val record = model.data.primaryStory.messageRecord as MmsMessageRecord
      val blur = record.slideDeck.thumbnailSlide?.placeholderBlur
      val (text: StoryTextPostModel?, image: Uri?) = if (record.storyType.isTextStory) {
        StoryTextPostModel.parseFrom(record) to null
      } else {
        null to record.slideDeck.thumbnailSlide?.uri
      }

      startActivityIfAble(
        StoryViewerActivity.createIntent(
          context = requireContext(),
          storyViewerArgs = StoryViewerArgs(
            recipientId = model.data.storyRecipient.id,
            storyId = -1L,
            isInHiddenStoryMode = model.data.isHidden,
            storyThumbTextModel = text,
            storyThumbUri = image,
            storyThumbBlur = blur,
            recipientIds = viewModel.getRecipientIds(model.data.isHidden, model.data.storyViewState == StoryViewState.UNVIEWED),
            isFromInfoContextMenuAction = isFromInfoContextMenuAction,
            isJumpToUnviewed = model.data.storyViewState == StoryViewState.UNVIEWED
          )
        ),
        options.toBundle()
      )
    }
  }

  private fun handleDeleteStory(model: StoriesLandingItem.Model) {
    lifecycleDisposable += StoryContextMenu.delete(requireContext(), model.data.primaryStory.messageRecord).subscribe()
  }

  private fun handleHideStory(model: StoriesLandingItem.Model) {
    StoryDialogs.hideStory(requireContext(), model.data.storyRecipient.getShortDisplayName(requireContext())) {
      viewModel.setHideStory(model.data.storyRecipient, true).subscribe {
        mainNavigationViewModel.snackbarRegistry.emit(
          SnackbarState(
            message = getString(R.string.StoriesLandingFragment__story_hidden),
            hostKey = MainSnackbarHostKey.MainChrome
          )
        )
      }
    }
  }

  @Suppress("OVERRIDE_DEPRECATION")
  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
  }

  private fun startActivityIfAble(intent: Intent, options: Bundle? = null) {
    if (viewModel.isTransitioningToAnotherScreen) {
      return
    }

    viewModel.isTransitioningToAnotherScreen = true
    startActivity(intent, options)
  }

  private fun isSearchOpen(): Boolean {
    return isSearchVisible()
  }

  private fun isSearchVisible(): Boolean {
    return mainToolbarViewModel.state.value.mode == MainToolbarMode.SEARCH
  }

  private fun closeSearchIfOpen(): Boolean {
    if (isSearchOpen()) {
      mainToolbarViewModel.setToolbarMode(MainToolbarMode.FULL)
      return true
    }
    return false
  }
}
