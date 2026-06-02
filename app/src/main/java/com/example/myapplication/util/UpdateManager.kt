package com.example.myapplication.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.myapplication.data.remote.SheetsApiService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

data class AppVersionResponse(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String
)

interface GitHubUpdateService {
    @GET("https://raw.githubusercontent.com/Moonglade-07/EatUp-Sales-Tracker/main/version.json")
    suspend fun getLatestVersion(): AppVersionResponse
}

class UpdateManager(private val context: Context) {
    
    private val currentVersionCode = 3 // Match this with build.gradle
    
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://raw.githubusercontent.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        
    private val service = retrofit.create(GitHubUpdateService::class.java)

    suspend fun checkForUpdate(): AppVersionResponse? {
        return try {
            val latest = service.getLatestVersion()
            if (latest.versionCode > currentVersionCode) latest else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun downloadUpdate(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
