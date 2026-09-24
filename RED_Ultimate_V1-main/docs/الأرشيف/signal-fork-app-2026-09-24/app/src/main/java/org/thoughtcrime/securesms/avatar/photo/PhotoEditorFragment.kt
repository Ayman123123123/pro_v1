package com.red.sovereign.avatar.photo

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.setFragmentResult
import org.signal.core.util.ThreadUtil
import org.signal.core.util.concurrent.REDExecutors
import com.red.sovereign.R
import com.red.sovereign.avatar.AvatarBundler
import com.red.sovereign.avatar.AvatarPickerStorage
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.scribbles.ImageEditorFragment

class PhotoEditorFragment : Fragment(R.layout.avatar_photo_editor_fragment), ImageEditorFragment.Controller {

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val args = PhotoEditorActivityArgs.fromBundle(requireArguments())
    val photo = AvatarBundler.extractPhoto(args.photoAvatar)
    val imageEditorFragment = ImageEditorFragment.newInstanceForAvatarEdit(photo.uri)

    childFragmentManager.commit {
      add(R.id.fragment_container, imageEditorFragment, IMAGE_EDITOR)
    }
  }

  override fun onTouchEventsNeeded(needed: Boolean) {
  }

  override fun onRequestFullScreen(fullScreen: Boolean, hideKeyboard: Boolean) {
  }

  override fun onDoneEditing() {
    val args = PhotoEditorActivityArgs.fromBundle(requireArguments())
    val applicationContext = requireContext().applicationContext
    val imageEditorFragment: ImageEditorFragment = childFragmentManager.findFragmentByTag(IMAGE_EDITOR) as ImageEditorFragment

    REDExecutors.BOUNDED.execute {
      val editedImageUri = imageEditorFragment.renderToSingleUseBlob()
      val size = AppDependencies.blobs.getFileSize(editedImageUri) ?: 0
      val inputStream = AppDependencies.blobs.getStream(applicationContext, editedImageUri)
      val onDiskUri = AvatarPickerStorage.save(applicationContext, inputStream)
      val photo = AvatarBundler.extractPhoto(args.photoAvatar)
      val database = REDDatabase.avatarPicker
      val newPhoto = photo.copy(uri = onDiskUri, size = size)

      database.update(newPhoto)
      AppDependencies.blobs.delete(requireContext(), photo.uri)

      ThreadUtil.runOnMain {
        setFragmentResult(REQUEST_KEY_EDIT, AvatarBundler.bundlePhoto(newPhoto))
      }
    }
  }

  override fun onCancelEditing() {
    requireActivity().finishAfterTransition()
  }

  override fun restoreState() {
  }

  override fun onMainImageLoaded() {
  }

  override fun onMainImageFailedToLoad() {
  }

  companion object {
    const val REQUEST_KEY_EDIT = "com.red.sovereign.avatar.photo.EDIT"

    private const val IMAGE_EDITOR = "image_editor"
  }
}
