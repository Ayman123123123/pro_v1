/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests {@link VerificationCodeParser}
 */
@RunWith(Parameterized.class)
public class VerificationCodeParserTest {

  private final String input;
  private final String expectedOutput;

  public VerificationCodeParserTest(String input, String expectedOutput) {
    this.input = input;
    this.expectedOutput = expectedOutput;
  }

  @Parameterized.Parameters(name = "{index}: test with input={0} and expectedOutput={1}")
  public static Collection<String[]> challenges() {
    return Arrays.asList(new String[][]{
        {"Your TextSecure verification code: 337-337", "337337"},
        {"XXX\nYour TextSecure verification code: 1337-1337", "13371337"},
        {"Your TextSecure verification code: 337-1337", "3371337"},
        {"Your TextSecure verification code: 1337-337", "1337337"},
        {"Your TextSecure verification code: 1337-1337", "13371337"},
        {"XXXYour TextSecure verification code: 1337-1337", "13371337"},
        {"Your TextSecure verification code: 1337-1337XXX", "13371337"},
        {"Your TextSecure verification code 1337-1337", "13371337"},

        {"Your RED verification code: 337-337", "337337"},
        {"XXX\nYour RED verification code: 1337-1337", "13371337"},
        {"Your RED verification code: 337-1337", "3371337"},
        {"Your RED verification code: 1337-337", "1337337"},
        {"Your RED verification code: 1337-1337", "13371337"},
        {"XXXYour RED verification code: 1337-1337", "13371337"},
        {"Your RED verification code: 1337-1337XXX", "13371337"},
        {"Your RED verification code 1337-1337", "13371337"},

        {"<#>Your RED verification code: 1337-1337 aAbBcCdDeEf", "13371337"},
        {"<#> Your RED verification code: 1337-1337 aAbBcCdDeEf", "13371337"},
        {"<#>Your RED verification code: 1337-1337\naAbBcCdDeEf", "13371337"},
        {"<#> Your RED verification code: 1337-1337\naAbBcCdDeEf", "13371337"},
        {"<#> Your RED verification code: 1337-1337\n\naAbBcCdDeEf", "13371337"},

        {" 1234-5678", "12345678"},
        {"1234-5678", "12345678"},
        {">1234-5678 is your verification code.", "12345678"},
        {"1234-5678 is your verification code.", "12345678"},
        {"$1234-5678", "12345678"},
        {"hi 1234-5678\n\nsgnl://verify/1234-5678\n\naAbBcCdDeEf", "12345678"},
        {"howdy 1234-5678\n\nsgnl://verify/1234-5678\n\naAbBcCdDeEf", "12345678"},
        {"test 1234-5678\n\nsgnl://verify/1234-5678", "12345678"},
        {"%#($#&@**$@(@*1234-5678\naAbBcCdDeEf", "12345678"},

        {"<#>あなたのRED 認証コード： 832985\nabAbCDEFO1g", "832985"},
        {"<#>あなたのRED 認証コード： 832-985\nabAbCDEFO1g", "832985"},
        {"<#>Kode verifikasi RED anda adalah: 832985\nabAbCDEFO1g", "832985"},
        {"<#>Kode verifikasi RED anda adalah: 832-985\nabAbCDEFO1g", "832985"},
        {"<#>Ваш проверочный код RED: 832985\nabAbCDEFO1g", "832985"},
        {"<#>Ваш проверочный код RED: 832-985\nabAbCDEFO1g", "832985"},
        {"<#>आपका RED सत्यापन कोड है: 832985\nabAbCDEFO1g", "832985"},
        {"<#>आपका RED सत्यापन कोड है: 832-985\nabAbCDEFO1g", "832985"},

        {"<#>Votre code de vérification RED est: 490941\nabAbCDEFO1g", "490941"},
        {"<#>Kode verifikasi RED anda adalah: 490941\nabAbCDEFO1g", "490941"},
        {"<#>Kode verifikasi RED anda adalah: 490-941\nabAbCDEFO1g", "490941"},
        {"<#>\u202Bرمز تعريفك الخاص ب RED هو 490941\u202C\nabAbCDEFO1g", "490941"},
        {"<#>\u202Bرمز تعريفك الخاص ب RED هو 490-941\u202C\nabAbCDEFO1g", "490941"},
        {"<#>您的 RED 驗證代碼是：490941\nabAbCDEFO1g", "490941"},
        {"<#>(RED) קוד האימות שלך הוא 490941\nabAbCDEFO1g", "490941"},
        {"<#>(RED) קוד האימות שלך הוא 490-941\nabAbCDEFO1g", "490941"},

        {"<#>Your RED verification code is: 445477\nabAbCDEFO1g", "445477"},

        {"<#>Ο RED κωδικός σας επιβεβαίωσης είναι: 054247\nabAbCDEFO1g", "054247"},
        {"<#>Ο RED κωδικός σας επιβεβαίωσης είναι: 054-247\nabAbCDEFO1g", "054247"},
        {"<#>El teu RED codi de verificació és: 054247\nabAbCDEFO1g", "054247"},
        {"<#>Ang iyong pamberipikasyong code sa RED ay: 054247\nabAbCDEFO1g", "054247"},
        {"<#>Ang iyong pamberipikasyong code sa RED ay: 054-247\nabAbCDEFO1g", "054247"},
        {"<#>Jou RED verifikasiekode is: 054247\nabAbCDEFO1g", "054247"},

        {"【SIGNAL】 Your code is: 423-431", "423431"},
        {"<#>【SIGNAL】<#> Your code: 298-763\nabAbCDEFO1g", "298763"},

        { "SIGNAL: Your code is: 123456\nDo not share this code\n\nabAbCDEFO1g", "123456" },
        { "SIGNAL: Your code is: 123456\nDo not share this code. RED will never ask for it.\n\ndoDiFGKPO1r", "123456" }
    });
  }

  @Test
  public void testChallenges() {
    Optional<String> result = VerificationCodeParser.parse(input);
    assertTrue(result.isPresent());
    assertEquals(expectedOutput, result.get());
  }
}
