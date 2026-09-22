package com.red.sovereign.groups.ui;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import com.red.sovereign.groups.GroupChangeBusyException;
import com.red.sovereign.groups.GroupInsufficientRightsException;
import com.red.sovereign.groups.GroupNotAMemberException;
import com.red.sovereign.groups.MembershipNotSuitableForV2Exception;
import org.whispersystems.signalservice.internal.push.exceptions.GroupTerminatedException;

import java.io.IOException;

public enum GroupChangeFailureReason {
  NO_RIGHTS,
  NOT_GV2_CAPABLE,
  NOT_ANNOUNCEMENT_CAPABLE,
  NOT_A_MEMBER,
  BUSY,
  NETWORK,
  GROUP_TERMINATED,
  OTHER;

  @SuppressLint("SuspiciousIndentation")
  public static @NonNull GroupChangeFailureReason fromException(@NonNull Throwable e) {
    if (e instanceof MembershipNotSuitableForV2Exception) return GroupChangeFailureReason.NOT_GV2_CAPABLE;
    if (e instanceof GroupTerminatedException)            return GroupChangeFailureReason.GROUP_TERMINATED;
    if (e instanceof IOException)                         return GroupChangeFailureReason.NETWORK;
    if (e instanceof GroupNotAMemberException)            return GroupChangeFailureReason.NOT_A_MEMBER;
    if (e instanceof GroupChangeBusyException)            return GroupChangeFailureReason.BUSY;
    if (e instanceof GroupInsufficientRightsException)    return GroupChangeFailureReason.NO_RIGHTS;
                                                          return GroupChangeFailureReason.OTHER;
  }
}
