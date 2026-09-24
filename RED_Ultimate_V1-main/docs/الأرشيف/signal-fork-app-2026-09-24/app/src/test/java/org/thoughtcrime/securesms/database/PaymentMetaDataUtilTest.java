package com.red.sovereign.database;

import org.junit.Test;
import com.red.sovereign.payments.proto.PaymentMetaData;
import org.signal.core.util.Util;

import java.util.Collections;

import okio.ByteString;

import static org.junit.Assert.assertArrayEquals;

public final class PaymentMetaDataUtilTest {

  @Test
  public void extract_single_public_key() {
    byte[] random = Util.getSecretBytes(32);
    byte[] bytes  = PaymentMetaDataUtil.receiptPublic(new PaymentMetaData.Builder().mobileCoinTxoIdentification(new PaymentMetaData.MobileCoinTxoIdentification.Builder().publicKey(Collections.singletonList(ByteString.of(random))).build()).build());

    assertArrayEquals(random, bytes);
  }
}
