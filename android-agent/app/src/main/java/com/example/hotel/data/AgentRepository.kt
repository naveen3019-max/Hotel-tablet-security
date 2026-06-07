package com.example.hotel.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import android.content.Context
import android.util.Log
import java.util.concurrent.TimeUnit

class AgentRepository(context: Context) {
    private val prefs = context.getSharedPreferences("agent", Context.MODE_PRIVATE)
    private val baseUrl = prefs.getString("backend_url", "https://hotel-backend-zqc1.onrender.com/") ?: "https://hotel-backend-zqc1.onrender.com/"
    
    init {
        Log.d("AgentRepository", "Initialized with baseUrl: $baseUrl")
    }
    
    companion object {
        @Volatile
        private var instance: AgentRepository? = null
        
        fun default(context: Context): AgentRepository {
            return instance ?: synchronized(this) {
                instance ?: AgentRepository(context).also { instance = it }
            }
        }
        
        private val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }
    
    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d("HTTP", message)
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    
    private val retrofitAlerts = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val alerts: AlertApi = retrofitAlerts.create(AlertApi::class.java)
}
