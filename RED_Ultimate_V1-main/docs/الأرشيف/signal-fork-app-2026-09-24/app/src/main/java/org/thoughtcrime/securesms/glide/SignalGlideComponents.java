package com.red.sovereign.glide;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.UnitModelLoader;
import com.bumptech.glide.load.resource.bitmap.BitmapDrawableEncoder;
import com.bumptech.glide.load.resource.gif.ByteBufferGifDecoder;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.load.resource.gif.StreamGifDecoder;

import org.signal.apng.ApngDecoder;
import org.signal.blurhash.BlurHash;
import org.signal.core.util.crypto.AttachmentSecret;
import org.signal.core.util.crypto.AttachmentSecretProvider;
import org.signal.glide.blurhash.BlurHashModelLoader;
import org.signal.glide.blurhash.BlurHashResourceDecoder;
import org.signal.glide.common.io.InputStreamFactory;
import org.signal.glide.decryptableuri.DecryptableUri;
import org.signal.glide.decryptableuri.DecryptableUriStreamLoader;
import org.signal.glide.load.resource.apng.decode.APNGDecoder;
import com.red.sovereign.badges.load.BadgeLoader;
import com.red.sovereign.badges.load.GiftBadgeModel;
import com.red.sovereign.badges.models.Badge;
import com.red.sovereign.contacts.avatars.ContactPhoto;
import com.red.sovereign.contacts.avatars.ContactPhotoLoader;
import com.red.sovereign.crypto.AppAttachmentSecretStore;
import com.red.sovereign.giph.model.ChunkedImageUrl;
import com.red.sovereign.glide.cache.ApngDrawableTranscoder;
import com.red.sovereign.glide.cache.ApngFrameDrawableTranscoder;
import com.red.sovereign.glide.cache.ApngInputStreamFactoryResourceDecoder;
import com.red.sovereign.glide.cache.ByteBufferApngDecoder;
import com.red.sovereign.glide.cache.EncryptedApngCacheDecoder;
import com.red.sovereign.glide.cache.EncryptedApngCacheEncoder;
import com.red.sovereign.glide.cache.EncryptedApngResourceEncoder;
import com.red.sovereign.glide.cache.EncryptedBitmapResourceEncoder;
import com.red.sovereign.glide.cache.EncryptedCacheDecoder;
import com.red.sovereign.glide.cache.EncryptedCacheEncoder;
import com.red.sovereign.glide.cache.EncryptedGifDrawableResourceEncoder;
import com.red.sovereign.glide.cache.InputStreamFactoryBitmapDecoder;
import com.red.sovereign.glide.cache.StreamApngDecoder;
import com.red.sovereign.glide.cache.StreamBitmapDecoder;
import com.red.sovereign.glide.cache.StreamFactoryApngDecoder;
import com.red.sovereign.glide.cache.StreamFactoryGifDecoder;
import com.red.sovereign.glide.cache.WebpSanDecoder;
import com.red.sovereign.mms.RegisterGlideComponents;
import com.red.sovereign.mms.REDGlideModule;
import com.red.sovereign.stickers.StickerRemoteUri;
import com.red.sovereign.stickers.StickerRemoteUriLoader;
import com.red.sovereign.stories.StoryTextPostModel;
import com.red.sovereign.util.ConversationShortcutPhoto;
import com.red.sovereign.util.RemoteConfig;

import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * The core logic for {@link REDGlideModule}. This is a separate class because it uses
 * dependencies defined in the main Gradle module.
 */
public class REDGlideComponents implements RegisterGlideComponents {

  @Override
  public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
    AttachmentSecret attachmentSecret = AttachmentSecretProvider.getInstance(context, AppAttachmentSecretStore.INSTANCE).getOrCreateAttachmentSecret();
    byte[]           secret           = attachmentSecret.getModernKey();

    registry.prepend(File.class, File.class, UnitModelLoader.Factory.getInstance());

    registry.prepend(InputStream.class, Bitmap.class, new WebpSanDecoder());

    registry.prepend(InputStream.class, new EncryptedCacheEncoder(secret, glide.getArrayPool()));

    registry.prepend(File.class, Bitmap.class, new EncryptedCacheDecoder<>(secret, new StreamBitmapDecoder(context, glide, registry)));

    StreamGifDecoder        streamGifDecoder        = new StreamGifDecoder(registry.getImageHeaderParsers(), new ByteBufferGifDecoder(context, registry.getImageHeaderParsers(), glide.getBitmapPool(), glide.getArrayPool()), glide.getArrayPool());
    StreamFactoryGifDecoder streamFactoryGifDecoder = new StreamFactoryGifDecoder(streamGifDecoder);
    registry.prepend(InputStream.class, GifDrawable.class, streamGifDecoder);
    registry.prepend(InputStreamFactory.class, GifDrawable.class, streamFactoryGifDecoder);
    registry.prepend(GifDrawable.class, new EncryptedGifDrawableResourceEncoder(secret));
    registry.prepend(File.class, GifDrawable.class, new EncryptedCacheDecoder<>(secret, streamGifDecoder));

    EncryptedBitmapResourceEncoder encryptedBitmapResourceEncoder = new EncryptedBitmapResourceEncoder(secret);
    registry.prepend(Bitmap.class, new EncryptedBitmapResourceEncoder(secret));
    registry.prepend(BitmapDrawable.class, new BitmapDrawableEncoder(glide.getBitmapPool(), encryptedBitmapResourceEncoder));


    if (RemoteConfig.newApngRenderer()) {
      registry.prepend(InputStreamFactory.class, ApngDecoder.class, new ApngInputStreamFactoryResourceDecoder());
      registry.prepend(ApngDecoder.class, new EncryptedApngResourceEncoder(secret));
      registry.prepend(File.class, ApngDecoder.class, new EncryptedApngCacheDecoder(secret));
      registry.register(ApngDecoder.class, Drawable.class, new ApngDrawableTranscoder());
    } else {
      ByteBufferApngDecoder    byteBufferApngDecoder    = new ByteBufferApngDecoder();
      StreamApngDecoder        streamApngDecoder        = new StreamApngDecoder(byteBufferApngDecoder);
      StreamFactoryApngDecoder streamFactoryApngDecoder = new StreamFactoryApngDecoder(byteBufferApngDecoder, glide, registry);

      registry.prepend(InputStream.class, APNGDecoder.class, streamApngDecoder);
      registry.prepend(InputStreamFactory.class, APNGDecoder.class, streamFactoryApngDecoder);
      registry.prepend(ByteBuffer.class, APNGDecoder.class, byteBufferApngDecoder);
      registry.prepend(APNGDecoder.class, new EncryptedApngCacheEncoder(secret));
      registry.prepend(File.class, APNGDecoder.class, new EncryptedCacheDecoder<>(secret, streamApngDecoder));
      registry.register(APNGDecoder.class, Drawable.class, new ApngFrameDrawableTranscoder());
    }

    registry.prepend(BlurHash.class, Bitmap.class, new BlurHashResourceDecoder());
    registry.prepend(StoryTextPostModel.class, Bitmap.class, new StoryTextPostModel.Decoder());

    registry.append(StoryTextPostModel.class, StoryTextPostModel.class, UnitModelLoader.Factory.getInstance());
    registry.append(ConversationShortcutPhoto.class, Bitmap.class, new ConversationShortcutPhoto.Loader.Factory(context));
    registry.append(ContactPhoto.class, InputStream.class, new ContactPhotoLoader.Factory(context));
    registry.append(DecryptableUri.class, InputStreamFactory.class, new DecryptableUriStreamLoader.Factory(context));
    registry.append(InputStreamFactory.class, Bitmap.class, new InputStreamFactoryBitmapDecoder(context, glide, registry));
    registry.append(ChunkedImageUrl.class, InputStream.class, new ChunkedImageUrlLoader.Factory());
    registry.append(StickerRemoteUri.class, InputStream.class, new StickerRemoteUriLoader.Factory());
    registry.append(BlurHash.class, BlurHash.class, new BlurHashModelLoader.Factory());
    registry.append(Badge.class, InputStream.class, BadgeLoader.createFactory());
    registry.append(GiftBadgeModel.class, InputStream.class, GiftBadgeModel.createFactory());
    registry.replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory());
  }
}
