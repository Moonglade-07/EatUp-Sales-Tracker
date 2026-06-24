package com.example.myapplication.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

data class AppVersionResponse(
    @SerializedName("versionCode") val versionCode: Int,
    @SerializedName("versionName") val versionName: String,
    @SerializedName("downloadUrl") val downloadUrl: String,
    @SerializedName("releaseNotes") val releaseNotes: String
)

interface GitHubUpdateService {
    @GET("Moonglade-07/EatUp-Sales-Tracker/main/version.json")
    suspend fun getLatestVersion(): AppVersionResponse
}

class UpdateManager(private val context: Context) {
    
    // Dynamic version detection via Android System
    private val currentVersionCode: Int
        get() = try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            0
        }
    
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://raw.githubusercontent.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        
    private val service = retrofit.create(GitHubUpdateService::class.java)

    suspend fun checkForUpdate(): AppVersionResponse? {
        return try {
            val latest = service.getLatestVersion()
            // Only suggest update if the server version is HIGHER than installed version
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
