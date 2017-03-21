package io.tradle.snappystorage;

/**
 * Created by tenaciousmv on 3/21/17.
 */

class KeyValuePair {
  private String key;
  private String plaintext;
  public KeyValuePair() {}

  public KeyValuePair setKey(String key) {
    this.key = key;
    return this;
  }

  public KeyValuePair setValue(String value) {
    this.plaintext = value;
    return this;
  }

  public String getKey() {
    return key;
  }

  public String getValue() {
    return plaintext;
  }

}
