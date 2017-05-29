# SnappyDB-based backend for AsyncStorage

Android only for now

## Install

```
yarn add react-native-async-storage-snappy
```

## Link in the native dependency

```
react-native link react-native-async-storage-snappy
```

## Usage

AsyncStorage currently doesn't provide a way of swapping out the backend implementation, so for now you can fork react-native and cherry-pick the commit from this [PR](https://github.com/facebook/react-native/pull/11972) 

When it's merged, you will instead be able to do `AsyncStorage.setBackend(AsyncSnappyStorage)`

## Contributing

PRs welcome!
