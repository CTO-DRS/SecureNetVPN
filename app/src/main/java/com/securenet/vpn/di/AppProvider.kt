package com.securenet.vpn.di

/**
 * Implemented by [com.securenet.vpn.SecureNetApp]; lets Services and
 * BroadcastReceivers reach the dependency graph from any context.
 */
interface AppProvider {
    fun provideContainer(): AppContainer
}
