package com.lidseeker.app

import android.app.Application
import android.content.Context
import com.lidseeker.app.data.Repository
import com.lidseeker.app.data.Settings

/** Minimal manual DI: holds the singleton Settings + Repository. */
class LidseekerApp : Application() {
    val settings: Settings by lazy { Settings(this) }
    val repository: Repository by lazy { Repository(settings) }
}

val Context.repository: Repository
    get() = (applicationContext as LidseekerApp).repository
