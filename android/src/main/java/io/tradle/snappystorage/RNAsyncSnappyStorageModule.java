/**
 * Copyright (c) 2015-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package io.tradle.snappystorage;

import android.support.annotation.Nullable;
import android.util.Base64;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.facebook.android.crypto.keychain.AndroidConceal;
import com.facebook.android.crypto.keychain.SharedPrefsBackedKeyChain;
import com.facebook.common.logging.FLog;
import com.facebook.crypto.Crypto;
import com.facebook.crypto.CryptoConfig;
import com.facebook.crypto.Entity;
import com.facebook.crypto.keychain.KeyChain;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.common.ModuleDataCleaner;
import com.snappydb.DB;
import com.snappydb.DBFactory;
import com.snappydb.KeyIterator;
import com.snappydb.SnappydbException;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

//import com.facebook.react.module.annotations.ReactModule;

public final class RNAsyncSnappyStorageModule
        extends ReactContextBaseJavaModule implements ModuleDataCleaner.Cleanable {

  // SQL variable number limit, defined by SQLITE_LIMIT_VARIABLE_NUMBER:
  // https://raw.githubusercontent.com/android/platform_external_sqlite/master/dist/sqlite3.c
  public static final String SNAPPY_ASYNC_STORAGE_MODULE = "RNAsyncSnappyStorage";
  public static final String E_ENCRYPTION_FIRST = "encrypt() must be the first call to AsyncStorage";
  public static final String E_ENCRYPTION_NOT_AVAILABLE = "encryption not available";

  private DB db;
  private boolean mShuttingDown = false;
  private Boolean cryptoAvailable = null;
  private Crypto crypto;

  public RNAsyncSnappyStorageModule(ReactApplicationContext reactContext) {
    super(reactContext);
  }

  @Override
  public String getName() {
    return "AsyncSnappyStorage";
  }

  @Override
  public void initialize() {
    super.initialize();
    mShuttingDown = false;
  }

  @Override
  public void onCatalystInstanceDestroy() {
    mShuttingDown = true;
    try {
      if (db != null) db.close();
    } catch (SnappydbException s) {
      log(s);
    }
  }

  @Override
  public void clearSensitiveData() {
    // Clear local storage. If fails, crash, since the app is potentially in a bad state and could
    // cause a privacy violation. We're still not recovering from this well, but at least the error
    // will be reported to the server.
    try {
      db.close();
      db.destroy();
      db = null;
    } catch (SnappydbException s) {
      log(s);
    }
  }

  @ReactMethod
  public void encrypt (final Callback callback) {
    if (crypto != null) {
      callback.invoke();
      return;
    }

    if (db != null) {
      callback.invoke(SnappyErrorUtil.getError(null, E_ENCRYPTION_FIRST));
      return;
    }

    if (cryptoAvailable == null) {
      KeyChain keyChain = new SharedPrefsBackedKeyChain(getReactApplicationContext(), CryptoConfig.KEY_256);
      crypto = AndroidConceal.get().createDefaultCrypto(keyChain);
      cryptoAvailable = crypto.isAvailable();
    }

    if (!cryptoAvailable) {
      crypto = null;
      callback.invoke(SnappyErrorUtil.getError(null, E_ENCRYPTION_NOT_AVAILABLE));
      return;
    }

    callback.invoke();
  }

  /**
   * Given an array of keys, this returns a map of (key, value) pairs for the keys found, and
   * (key, null) for the keys that haven't been found.
   */
  @ReactMethod
  public void multiGet(final ReadableArray keys, final Callback callback) {
    if (keys == null) {
      callback.invoke(SnappyErrorUtil.getInvalidKeyError(null), null);
      return;
    }

    if (!ensureDatabase(callback)) {
      return;
    }

    WritableArray data = Arguments.createArray();
    for (int i = 0; i< keys.size(); i++) {
      String key = keys.getString(i);
      WritableArray row = Arguments.createArray();
      row.pushString(key);
      String value;
      try {
        value = get(key);
        row.pushString(value);
      } catch (SnappydbException s) {
        row.pushNull();
      }

      data.pushArray(row);
    }

    callback.invoke(null, data);
  }

  /**
   * Inserts multiple (key, value) pairs. If one or more of the pairs cannot be inserted, this will
   * return AsyncLocalStorageFailure, but all other pairs will have been inserted.
   * The insertion will replace conflicting (key, value) pairs.
   */
  @ReactMethod
  public void multiSet(final ReadableArray keyValueArray, final Callback callback) {
    if (keyValueArray.size() == 0) {
      callback.invoke(SnappyErrorUtil.getInvalidKeyError(null));
      return;
    }

    if (!ensureDatabase(callback)) {
      return;
    }

    WritableMap error = null;
    try {
      for (int i = 0; i < keyValueArray.size(); i++) {
        ReadableArray pair = keyValueArray.getArray(i);
        if (pair.size() != 2) {
          error = SnappyErrorUtil.getInvalidValueError(null);
          break;
        }

        put(pair.getString(0), pair.getString(1));
      }
    } catch (SnappydbException s) {
      log(s);
      error = SnappyErrorUtil.getError(s.getMessage());
    }

    if (error != null) {
      callback.invoke(error);
    } else {
      callback.invoke();
    }
  }

  /**
   * Removes all rows of the keys given.
   */
  @ReactMethod
  public void multiRemove(final ReadableArray keys, final Callback callback) {
    if (keys.size() == 0) {
      callback.invoke(SnappyErrorUtil.getInvalidKeyError(null));
      return;
    }

    if (!ensureDatabase(callback)) {
      return;
    }

    WritableMap error = null;
    try {
      for (int i = 0; i < keys.size(); i++) {
        db.del(keys.getString(i));
      }
    } catch (SnappydbException s) {
      log(s);
      error = SnappyErrorUtil.getError(s.getMessage());
    }

    if (error != null) {
      callback.invoke(error);
    } else {
      callback.invoke();
    }
  }

  /**
   * Given an array of (key, value) pairs, this will merge the given values with the stored values
   * of the given keys, if they exist.
   */
  @ReactMethod
  public void multiMerge(final ReadableArray keyValueArray, final Callback callback) {
    if (!ensureDatabase(callback)) {
      return;
    }

    WritableMap error = null;
    try {
      for (int idx = 0; idx < keyValueArray.size(); idx++) {
        ReadableArray pair = keyValueArray.getArray(idx);
        if (pair.size() != 2) {
          error = SnappyErrorUtil.getInvalidValueError(null);
          break;
        }

        String key = pair.getString(0);
        if (key == null) {
          error = SnappyErrorUtil.getInvalidKeyError(null);
          break;
        }

        String val = pair.getString(1);
        if (val == null) {
          error = SnappyErrorUtil.getInvalidValueError(null);
          return;
        }

        if (!merge(key, val)) {
          error = SnappyErrorUtil.getDBError(null);
          return;
        }
      }
    } catch (Exception e) {
      log(e);
      error = SnappyErrorUtil.getError(e.getMessage());
    }

    if (error != null) {
      callback.invoke(error);
    } else {
      callback.invoke();
    }
  }

  /**
   * Clears the database.
   */
  @ReactMethod
  public void clear(final Callback callback) {
    if (!ensureDatabase(callback)) {
      return;
    }

    try {
      db.close();
      db.destroy();
      db = null;
      callback.invoke();
    } catch (Exception e) {
      log(e);
      callback.invoke(SnappyErrorUtil.getError(e.getMessage()));
    }
  }

  /**
   * Returns an array with all keys from the database.
   */
  @ReactMethod
  public void getAllKeys(final Callback callback) {
    if (!ensureDatabase(callback)) {
      return;
    }

    WritableArray data = Arguments.createArray();
    WritableMap error = null;
    KeyIterator it = null;
    try {
      it = db.allKeysIterator();
      for (String[] batch : it.byBatch(500)) {
        for (String key : batch) {
          data.pushString(key);
        }
      }
    } catch (SnappydbException s) {
      log(s);
      error = SnappyErrorUtil.getError(s.getMessage());
    } finally {
      if (it != null) {
        try {
          it.close();
        } catch (Exception e) {
          log(e);
        }
      }
    }

    if (error != null) {
      callback.invoke(error, null);
    } else {
      callback.invoke(null, data);
    }
  }

  private void put(final String key, final String val) throws SnappydbException {
    if (crypto != null) {
      db.put(key, new KeyValuePair().setKey(key).setValue(val));
      return;
    }

    db.put(key, val);
  }

  private String get(final String key) throws SnappydbException {
    if (crypto != null) {
      return db.getObject(key, KeyValuePair.class).getValue();
    }

    return db.get(key);
  }

  private void log (Exception e) {
    FLog.w(SNAPPY_ASYNC_STORAGE_MODULE, e.getMessage(), e);
  }

  /**
   * Verify the database is open for reads and writes.
   */
  private boolean ensureDatabase(@Nullable final Callback callback) {
    WritableMap error = null;
    if (mShuttingDown) {
      error = SnappyErrorUtil.getDBError(null);
    } else {
      if (db == null) {
        try {
          db = DBFactory.open(this.getReactApplicationContext(), SNAPPY_ASYNC_STORAGE_MODULE);
          setupEncryption();
        } catch (SnappydbException e) {
          log(e);
          error = SnappyErrorUtil.getError(e.getMessage());
        }
      }
    }

    if (error != null) {
      callback.invoke(error);
      return false;
    }

    return true;
  }

  private void setupEncryption () {
    db.getKryoInstance().addDefaultSerializer(KeyValuePair.class, new Serializer<KeyValuePair>() {

      @Override
      public void write(Kryo kryo, Output output, KeyValuePair kv) {
        byte[] plaintextBytes = kv.getValue().getBytes();
        String key = kv.getKey();
        String ciphertext;
        try {
          byte[] ciphertextBytes = crypto.encrypt(plaintextBytes, Entity.create(key));
          ciphertext = Base64.encodeToString(ciphertextBytes, Base64.NO_WRAP);
        } catch (Exception e) {
          log(e);
          throw new RuntimeException(e);
        }

        output.writeString(key);
        output.writeString(ciphertext);
      }

      @Override
      public KeyValuePair read(Kryo kryo, Input input, Class<KeyValuePair> type) {
        String key = input.readString();
        byte[] ciphertextBytes = Base64.decode(input.readString(), Base64.NO_WRAP);
        String plaintext = null;
        try {
          byte[] plaintextBytes = crypto.decrypt(ciphertextBytes, Entity.create(key));
          plaintext = new String(plaintextBytes);
        } catch (Exception e) {
          log(e);
          throw new RuntimeException(e);
        }

        return new KeyValuePair()
          .setKey(key)
          .setValue(plaintext);
      }
    });
  }

  private boolean merge (final String key, final String val) throws SnappydbException, JSONException {
    String oldValue;
    try {
      oldValue = get(key);
    } catch (SnappydbException s) {
      put(key, val);
      return true;
    }

    JSONObject oldJSON = new JSONObject(oldValue);
    JSONObject newJSON = new JSONObject(val);
    deepMergeInto(oldJSON, newJSON);
    String newVal = oldJSON.toString();
    put(key, newVal);
    return true;
  }

  private static void deepMergeInto(JSONObject oldJSON, JSONObject newJSON)
          throws JSONException {
    Iterator<?> keys = newJSON.keys();
    while (keys.hasNext()) {
      String key = (String) keys.next();

      JSONObject newJSONObject = newJSON.optJSONObject(key);
      JSONObject oldJSONObject = oldJSON.optJSONObject(key);
      if (newJSONObject != null && oldJSONObject != null) {
        deepMergeInto(oldJSONObject, newJSONObject);
        oldJSON.put(key, oldJSONObject);
      } else {
        oldJSON.put(key, newJSON.get(key));
      }
    }
  }
}
