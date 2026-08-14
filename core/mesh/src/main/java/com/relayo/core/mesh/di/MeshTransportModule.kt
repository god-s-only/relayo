package com.relayo.core.mesh.di

import com.relayo.core.mesh.BleMeshMessenger
import com.relayo.core.mesh.BlePeerScanner
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
        impl:BlePeerScanner
    ):PeerScanner

    @Binds
    abstract fun bindMeshMessenger(
        impl:BleMeshMessenger
    ):MeshMessenger
}