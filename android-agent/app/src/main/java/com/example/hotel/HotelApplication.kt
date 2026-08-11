package com.example.hotel

import android.app.Application
import com.example.hotel.security.DeviceIdentity

class HotelApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DeviceIdentity.load(this)
    }
}
