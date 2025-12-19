package protect.card_locker.async

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun <T> CompatCallable<T>.runSuspending(): T? {
    withContext(
        Dispatchers.Main
    ){
        onPreExecute()
    }
    val result: T? = withContext(Dispatchers.IO){
        call()
    }
    withContext(Dispatchers.Main){
        onPostExecute(result)
    }
    return result
}