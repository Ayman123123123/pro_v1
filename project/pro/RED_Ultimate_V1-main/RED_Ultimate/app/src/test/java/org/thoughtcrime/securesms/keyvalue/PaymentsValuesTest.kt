package com.red.sovereign.keyvalue

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import com.red.sovereign.util.RemoteConfig

class PaymentsValuesTest {

  private lateinit var paymentValues: PaymentsValues

  @Before
  fun setup() {
    mockkObject(RemoteConfig)
    mockkObject(REDStore)

    paymentValues = mockk()
    every { paymentValues.paymentsAvailability } answers { callOriginal() }

    every { REDStore.payments } returns paymentValues

    every { REDStore.account.isRegistered } returns true
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `when unregistered, expect NOT_IN_REGION`() {
    every { REDStore.account.isRegistered } returns false

    assertEquals(PaymentsAvailability.NOT_IN_REGION, REDStore.payments.paymentsAvailability)
  }

  @Test
  fun `when flag disabled and no account, expect DISABLED_REMOTELY`() {
    every { REDStore.account.e164 } returns "+15551234567"
    every { paymentValues.mobileCoinPaymentsEnabled() } returns false
    every { RemoteConfig.payments } returns false
    every { RemoteConfig.paymentsCountryBlocklist } returns ""

    assertEquals(PaymentsAvailability.DISABLED_REMOTELY, REDStore.payments.paymentsAvailability)
  }

  @Test
  fun `when flag disabled but has account, expect WITHDRAW_ONLY`() {
    every { REDStore.account.e164 } returns "+15551234567"
    every { paymentValues.mobileCoinPaymentsEnabled() } returns true
    every { RemoteConfig.payments } returns false
    every { RemoteConfig.paymentsCountryBlocklist } returns ""

    assertEquals(PaymentsAvailability.WITHDRAW_ONLY, REDStore.payments.paymentsAvailability)
  }

  @Test
  fun `when flag enabled and no account, expect REGISTRATION_AVAILABLE`() {
    every { REDStore.account.e164 } returns "+15551234567"
    every { paymentValues.mobileCoinPaymentsEnabled() } returns false
    every { RemoteConfig.payments } returns true
    every { RemoteConfig.paymentsCountryBlocklist } returns ""

    assertEquals(PaymentsAvailability.REGISTRATION_AVAILABLE, REDStore.payments.paymentsAvailability)
  }

  @Test
  fun `when flag enabled and has account, expect WITHDRAW_AND_SEND`() {
    every { REDStore.account.e164 } returns "+15551234567"
    every { paymentValues.mobileCoinPaymentsEnabled() } returns true
    every { RemoteConfig.payments } returns true
    every { RemoteConfig.paymentsCountryBlocklist } returns ""

    assertEquals(PaymentsAvailability.WITHDRAW_AND_SEND, REDStore.payments.paymentsAvailability)
  }

  @Test
  fun `when flag enabled and no account and in the country blocklist, expect NOT_IN_REGION`() {
    every { REDStore.account.e164 } returns "+15551234567"
    every { paymentValues.mobileCoinPaymentsEnabled() } returns false
    every { RemoteConfig.payments } returns true
    every { RemoteConfig.paymentsCountryBlocklist } returns "1"

    assertEquals(PaymentsAvailability.NOT_IN_REGION, REDStore.payments.paymentsAvailability)
  }

  @Test
  fun `when flag enabled and has account and in the country blocklist, expect WITHDRAW_ONLY`() {
    every { REDStore.account.e164 } returns "+15551234567"
    every { paymentValues.mobileCoinPaymentsEnabled() } returns true
    every { RemoteConfig.payments } returns true
    every { RemoteConfig.paymentsCountryBlocklist } returns "1"

    assertEquals(PaymentsAvailability.WITHDRAW_ONLY, REDStore.payments.paymentsAvailability)
  }
}
