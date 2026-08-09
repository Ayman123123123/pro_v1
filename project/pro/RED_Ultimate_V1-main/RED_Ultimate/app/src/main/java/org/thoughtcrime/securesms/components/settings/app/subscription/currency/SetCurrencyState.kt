package com.red.sovereign.components.settings.app.subscription.currency

import java.util.Currency

data class SetCurrencyState(
  val selectedCurrencyCode: String = "",
  val currencies: List<Currency> = listOf()
)
