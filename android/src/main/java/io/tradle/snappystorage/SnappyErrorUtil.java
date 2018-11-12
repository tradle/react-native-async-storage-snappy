package io.tradle.snappystorage;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;

import javax.annotation.Nullable;

/**
 * Created by tenaciousmv on 1/18/17.
 * /**
 * Copyright (c) 2015-present, Facebook, Inc.
 * All rights reserved.
 * <p>
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */


public class SnappyErrorUtil {

  /**
   * Create Error object to be passed back to the JS callback.
   */
  /* package */ static WritableMap getError(@Nullable String key, String errorMessage) {
    WritableMap errorMap = Arguments.createMap();
    errorMap.putString("message", errorMessage);
    if (key != null) {
      errorMap.putString("key", key);
    }
    return errorMap;
  }

  /* package */ static WritableMap getError(String errorMessage) {
    return getError(null, errorMessage);
  }

  /* package */ static WritableMap getInvalidKeyError(@Nullable String key) {
    return getError(key, "Invalid key");
  }

  /* package */ static WritableMap getInvalidValueError(@Nullable String key) {
    return getError(key, "Invalid Value");
  }

  /* package */ static WritableMap getDBError(@Nullable String key) {
    return getError(key, "Database Error");
  }

  /* package */ static WritableMap getInvalidRangeError(@Nullable String key) {
    return getError(key, "Invalid Range");
  }

  /* package */ static WritableMap getInvalidPrefixError(@Nullable String key) {
    return getError(key, "Invalid Prefix");
  }
}
