package com.pointquest.android.app

import com.pointquest.android.core.auth.SessionState
import com.pointquest.android.core.network.RemoteHostStore
import com.pointquest.android.data.auth.AuthRepository
import com.pointquest.android.data.orders.OrdersRepository
import com.pointquest.android.data.points.PointsRepository
import com.pointquest.android.data.practice.PracticeRepository
import com.pointquest.android.data.products.ProductImageUrlFactory
import com.pointquest.android.data.products.ProductsRepository
import com.pointquest.android.feature.practice.PracticeDraftStore

interface AppDependencies {
    val sessionState: SessionState
    val remoteHostStore: RemoteHostStore
    val appDataSync: AppDataSync
    val authRepository: AuthRepository
    val practiceRepository: PracticeRepository
    val practiceDraftStore: PracticeDraftStore
    val pointsRepository: PointsRepository
    val productsRepository: ProductsRepository
    val ordersRepository: OrdersRepository
    val productImageUrlFactory: ProductImageUrlFactory
}
