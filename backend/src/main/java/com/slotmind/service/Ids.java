package com.slotmind.service;

import java.util.UUID;

public final class Ids {
  public static final String APPOINTMENT_PREFIX = "APT-";
  public static final String SUGGESTION_PREFIX = "SUG-";
  private static final int SUFFIX_LENGTH = 8;

  private Ids() {}

  public static String next(String prefix) {
    return prefix + UUID.randomUUID().toString().substring(0, SUFFIX_LENGTH).toUpperCase();
  }
}
