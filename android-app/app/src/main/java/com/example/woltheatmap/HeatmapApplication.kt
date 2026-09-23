package com.example.woltheatmap

import android.app.Application
import com.example.woltheatmap.data.local.AppDatabase
import com.example.woltheatmap.data.remote.HeatmapApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class HeatmapApplication : Application() {

    // 10.0.2.2 is the Android emulator's alias for the host machine's localhost.
    // Change to your dev machine's LAN IP when testing on a physical device.
    private val serverUrl = "http://10.0.2.2:3000/"

    val database by lazy { AppDatabase.getInstance(this) }

    val api: HeatmapApi by lazy {
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        Retrofit.Builder()
            .baseUrl(serverUrl)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(HeatmapApi::class.java)
    }
}
