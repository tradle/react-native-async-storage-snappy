
import {
  NativeModules
} from 'react-native'

const AsyncStorage = NativeModules.AsyncSnappyStorage
const { encrypt } = AsyncStorage
AsyncStorage.encrypt = function () {
  return new Promise((resolve, reject) => {
    encrypt(err => {
      if (err) {
        return reject(new Error(err))
      }

      resolve()
    })
  })
}

export default AsyncStorage
