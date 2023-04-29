import com.example.viacom.Album
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create
import retrofit2.http.GET


const val ALBUM_N = 10
const val PHOTOS_N = 5

class ImageRepository(private val cacheDir: File) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val BASE_URL = "https://jsonplaceholder.typicode.com/"

    val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build();

    val imageClient = retrofit.create<ImageClientApi>()

    fun getPhotos() {

        scope.launch {
            val list = imageClient.getAlbumPhotos()
            iterateList(list)
        }
    }

    private suspend fun iterateList(list: ArrayList<Album>) = withContext(Dispatchers.IO) {

        //to track the photos to download per album, "dir.listFiles().size" didn't work (returns 0)  as files are being downloaded parallely
        val map = HashMap<Int, Int>()

        //assuming this is sorted by album id
        list.forEach {

            if (it.albumId > ALBUM_N)
                return@withContext


            val dir = File(cacheDir, "${it.albumId}")

            if (dir.exists().not()) {
                dir.mkdir()
                map[it.albumId] = 0

            } else {

                if (map.getOrDefault(it.albumId, 0) >= PHOTOS_N) {
                    return@forEach
                }
            }

            map[it.albumId] = map.getOrDefault(it.albumId, 0) + 1

            scope.launch(Dispatchers.IO) {
                downloadAndSave(it.url, dir)
            }
        }


    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()


    private fun downloadAndSave(url: String, dir: File) {

        val call = client.newCall(Request.Builder().get().url(url).build())
        try {
            val response = call.execute()

            if (response.isSuccessful) {
                val iStream = response.body?.byteStream()

                iStream?.let {

                    val fileName = url.split("/").last()
                    val file = File(dir, fileName)
                    writeInputStreamToFile(iStream, file)
                }
            } else {
                // Handle the error
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }


    private fun writeInputStreamToFile(inputStream: InputStream, file: File) {

        val outputStream = FileOutputStream(file)
        outputStream.write(inputStream.readBytes())
        outputStream.flush()
        outputStream.close()
        inputStream.close()
    }

}


interface ImageClientApi {

    @GET("photos")
    suspend fun getAlbumPhotos(): ArrayList<Album>
}
