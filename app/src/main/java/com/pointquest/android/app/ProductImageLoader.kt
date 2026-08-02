package com.pointquest.android.app

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.Call

internal fun createProductImageLoader(
    context: Context,
    callFactory: Call.Factory,
): ImageLoader = ImageLoader.Builder(context.applicationContext)
    .components {
        add(OkHttpNetworkFetcherFactory(callFactory = { callFactory }))
    }
    .build()
