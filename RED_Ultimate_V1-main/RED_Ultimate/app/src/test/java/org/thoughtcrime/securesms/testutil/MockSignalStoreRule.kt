/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.testutil

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.rules.ExternalResource
import com.red.sovereign.keyvalue.AccountValues
import com.red.sovereign.keyvalue.BackupValues
import com.red.sovereign.keyvalue.EmojiValues
import com.red.sovereign.keyvalue.InAppPaymentValues
import com.red.sovereign.keyvalue.InternalValues
import com.red.sovereign.keyvalue.MiscellaneousValues
import com.red.sovereign.keyvalue.NotificationProfileValues
import com.red.sovereign.keyvalue.PaymentsValues
import com.red.sovereign.keyvalue.PhoneNumberPrivacyValues
import com.red.sovereign.keyvalue.RegistrationValues
import com.red.sovereign.keyvalue.ReleaseChannelValues
import com.red.sovereign.keyvalue.SettingsValues
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.keyvalue.StorageServiceValues
import com.red.sovereign.keyvalue.StoryValues
import com.red.sovereign.keyvalue.SvrValues
import com.red.sovereign.keyvalue.UiHintValues
import kotlin.reflect.KClass

/**
 * Mocks [REDStore] to return mock versions of the various values. Mocks will default to not be relaxed (each
 * method call on them will need to be mocked) except for unit functions which will do nothing.
 *
 * Expand mocked values as necessary when needed.
 *
 * @param relaxed Set of value classes that should default to relaxed thus defaulting all methods. Useful
 * when value is not part of the input state under test but called within the under test code.
 */
@Suppress("MemberVisibilityCanBePrivate")
class MockREDStoreRule(private val relaxed: Set<KClass<*>> = emptySet()) : ExternalResource() {

  lateinit var account: AccountValues
    private set

  lateinit var phoneNumberPrivacy: PhoneNumberPrivacyValues
    private set

  lateinit var registration: RegistrationValues
    private set

  lateinit var svr: SvrValues
    private set

  lateinit var emoji: EmojiValues
    private set

  lateinit var inAppPayments: InAppPaymentValues
    private set

  lateinit var backup: BackupValues
    private set

  lateinit var settings: SettingsValues
    private set

  lateinit var releaseChannel: ReleaseChannelValues
    private set

  lateinit var storageService: StorageServiceValues
    private set

  lateinit var internal: InternalValues
    private set

  lateinit var misc: MiscellaneousValues
    private set

  lateinit var story: StoryValues
    private set

  lateinit var uiHints: UiHintValues
    private set

  lateinit var payments: PaymentsValues
    private set

  lateinit var notificationProfile: NotificationProfileValues
    private set

  override fun before() {
    account = mockk(relaxed = relaxed.contains(AccountValues::class), relaxUnitFun = true)
    phoneNumberPrivacy = mockk(relaxed = relaxed.contains(PhoneNumberPrivacyValues::class), relaxUnitFun = true)
    registration = mockk(relaxed = relaxed.contains(RegistrationValues::class), relaxUnitFun = true)
    svr = mockk(relaxed = relaxed.contains(SvrValues::class), relaxUnitFun = true)
    emoji = mockk(relaxed = relaxed.contains(EmojiValues::class), relaxUnitFun = true)
    inAppPayments = mockk(relaxed = relaxed.contains(InAppPaymentValues::class), relaxUnitFun = true)
    backup = mockk(relaxed = relaxed.contains(BackupValues::class), relaxUnitFun = true)
    settings = mockk(relaxed = relaxed.contains(SettingsValues::class), relaxUnitFun = true)
    releaseChannel = mockk(relaxed = relaxed.contains(ReleaseChannelValues::class), relaxUnitFun = true)
    storageService = mockk(relaxed = relaxed.contains(StorageServiceValues::class), relaxUnitFun = true)
    internal = mockk(relaxed = relaxed.contains(InternalValues::class), relaxUnitFun = true)
    misc = mockk(relaxed = relaxed.contains(MiscellaneousValues::class), relaxUnitFun = true)
    story = mockk(relaxed = relaxed.contains(StoryValues::class), relaxUnitFun = true)
    uiHints = mockk(relaxed = relaxed.contains(UiHintValues::class), relaxUnitFun = true)
    payments = mockk(relaxed = relaxed.contains(PaymentsValues::class), relaxUnitFun = true)
    notificationProfile = mockk(relaxed = relaxed.contains(NotificationProfileValues::class), relaxUnitFun = true)

    mockkObject(REDStore)
    every { REDStore.account } returns account
    every { REDStore.phoneNumberPrivacy } returns phoneNumberPrivacy
    every { REDStore.registration } returns registration
    every { REDStore.svr } returns svr
    every { REDStore.emoji } returns emoji
    every { REDStore.inAppPayments } returns inAppPayments
    every { REDStore.backup } returns backup
    every { REDStore.settings } returns settings
    every { REDStore.releaseChannel } returns releaseChannel
    every { REDStore.storageService } returns storageService
    every { REDStore.internal } returns internal
    every { REDStore.misc } returns misc
    every { REDStore.story } returns story
    every { REDStore.uiHints } returns uiHints
    every { REDStore.payments } returns payments
    every { REDStore.notificationProfile } returns notificationProfile
  }

  override fun after() {
    unmockkObject(REDStore)
  }
}
