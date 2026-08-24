package com.relayo.core.mesh.di

import com.relayo.core.mesh.HybridMeshMessenger
import com.relayo.core.mesh.HybridPeerScanner
import com.relayo.core.transport.MeshMessenger
import com.relayo.core.transport.PeerScanner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class MeshTransportModule {

    @Binds
    abstract fun bindPeerScanner(
        impl:HybridPeerScanner
    ):PeerScanner

    @Binds
    abstract fun bindMeshMessenger(
        impl:HybridMeshMessenger
    ):MeshMessenger
}