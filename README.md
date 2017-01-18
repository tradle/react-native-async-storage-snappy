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

AsyncStorage currently doesn't provide a way of swapping out the backend implementation so the easiest way to hack it in is to override the NativeModules.RCTAsyncRocksDBStorage:

```js
import { NativeModules } from 'react-native'
import AsyncSnappyStorage from 'react-native-async-storage-snappy'
NativeModules.AsyncRocksDBStorage = AsyncSnappyStorage
```

PRs welcome!
