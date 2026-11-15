package com.example

import android.app.Application
import com.example.data.download.DownloadEngine
import com.example.data.download.LinkResolver
import com.example.data.local.AppDatabase
import com.example.data.local.UserPreferences
import com.example.data.repository.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class LinkStashApplication : Application() {

    lateinit var repository: VaultRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        val okHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val database = AppDatabase.getInstance(this)
        val linkResolver = LinkResolver(okHttpClient)
        val downloadEngine = DownloadEngine(
            context = this,
            client = okHttpClient,
            linkResolver = linkResolver,
            savedItemDao = database.savedItemDao()
        )
        val preferences = UserPreferences(this)

        repository = VaultRepository(
            context = this,
            folderDao = database.folderDao(),
            savedItemDao = database.savedItemDao(),
            linkResolver = linkResolver,
            downloadEngine = downloadEngine,
            userPreferences = preferences
        )

        appScope.launch {
            repository.ensureSeededFolders()
        }
    }
}
