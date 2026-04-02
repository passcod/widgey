package com.widgey.data.db

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Simple change notification bus. Each DAO calls the appropriate `invalidate*()` method after
 * every write; observe-style Flow functions subscribe to the corresponding channel and re-query.
 */
internal class DatabaseChangeNotifier {

    private val _nodes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val nodes: SharedFlow<Unit> = _nodes.asSharedFlow()

    private val _widgetConfig = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val widgetConfig: SharedFlow<Unit> = _widgetConfig.asSharedFlow()

    private val _syncQueue = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val syncQueue: SharedFlow<Unit> = _syncQueue.asSharedFlow()

    private val _settings = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val settings: SharedFlow<Unit> = _settings.asSharedFlow()

    fun invalidateNodes() { _nodes.tryEmit(Unit) }
    fun invalidateWidgetConfig() { _widgetConfig.tryEmit(Unit) }
    fun invalidateSyncQueue() { _syncQueue.tryEmit(Unit) }
    fun invalidateSettings() { _settings.tryEmit(Unit) }
}

