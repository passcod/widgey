package com.widgey.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.widgey.data.entity.NodeEntity
import com.widgey.data.entity.WidgetConfigEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetConfigDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var widgetConfigDao: WidgetConfigDao
    private lateinit var nodeDao: NodeDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.inMemory(context)
        widgetConfigDao = database.widgetConfigDao()
        nodeDao = database.nodeDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveConfig() = runTest {
        val config = WidgetConfigEntity(widgetId = 1, nodeId = "node-1")

        widgetConfigDao.insert(config)
        val retrieved = widgetConfigDao.getConfig(1)

        assertNotNull(retrieved)
        assertEquals(1, retrieved?.widgetId)
        assertEquals("node-1", retrieved?.nodeId)
    }

    @Test
    fun getConfigReturnsNullForNonexistent() = runTest {
        val result = widgetConfigDao.getConfig(999)
        assertNull(result)
    }

    @Test
    fun insertWithNullNodeId() = runTest {
        val config = WidgetConfigEntity(widgetId = 1, nodeId = null)

        widgetConfigDao.insert(config)
        val retrieved = widgetConfigDao.getConfig(1)

        assertNotNull(retrieved)
        assertNull(retrieved?.nodeId)
    }

    @Test
    fun getAllConfigsReturnsAllWidgets() = runTest {
        widgetConfigDao.insert(WidgetConfigEntity(widgetId = 1, nodeId = "node-1"))
        widgetConfigDao.insert(WidgetConfigEntity(widgetId = 2, nodeId = "node-2"))
        widgetConfigDao.insert(WidgetConfigEntity(widgetId = 3, nodeId = null))

        val allConfigs = widgetConfigDao.getAllConfigs()

        assertEquals(3, allConfigs.size)
    }

    @Test
    fun getConfiguredWidgetsExcludesNullNodeIds() = runTest {
        widgetConfigDao.insert(WidgetConfigEntity(widgetId = 1, nodeId = "node-1"))
        widgetConfigDao.insert(WidgetConfigEntity(widgetId = 2, nodeId = "node-2"))
        widgetConfigDao.insert(WidgetConfigEntity(widgetId = 3, nodeId = null))

        val configured = widgetConfigDao.getConfiguredWidgets()

        assertEquals(2, configured.size)
        assertTrue(configured.all { it.nodeId != null })
    }

    @Test
    fun getAllNodeIdsReturnsDistinctIds() = runTest {
        widgetConfigDao.insert(WidgetConfigEntity(widgetId = 1, nodeId = "node-1"))
        widgetConfigDao.insert(WidgetConfigEntity(widgetId = 2, nodeId = "node-2"))
        widgetConfigDao.insert(WidgetConfigEntity(widgetId = 3, nodeId = "node-1")) // duplicate
        widgetConfigDao.insert(WidgetConfigEntity(widgetId = 4, nodeId = null))

        val nodeIds = widgetConfigDao.getAllNodeIds()

        assertEquals(2, nodeIds.size)
        assertTrue(nodeIds.contains("node-1"))
        assertTrue(nodeIds.contains("node-2"))
    }

    @Test
    fun updateNodeIdChangesExistingConfig() = runTest {
        widgetConfigDao.insert(WidgetConfigEntity(widgetId = 1, nodeId = "old-node"))

        widgetConfigDao.updateNodeId(1, "new-node")

        val updated = widgetConfigDao.getConfig(1)
        assertEquals("new-node", updated?.nodeId)
    }

    @Test
    fun updateNodeIdToNull() = runTest {
        widgetConfigDao.insert(WidgetConfigEntity(widgetId = 1, nodeId = "some-node"))

        widgetConfigDao.updateNodeId(1, null)

        val updated = widgetConfigDao.getConfig(1)
        assertNull(updated?.nodeId)
    }

    @Test
    fun deleteRemovesConfig() = runTest {
        widgetConfigDao.insert(WidgetConfigEntity(widgetId = 1, nodeId = "node-1"))

        widgetConfigDao.delete(1)

        val result = widgetConfigDao.getConfig(1)
        assertNull(result)
    }

    @Test
    fun deleteAllRemovesMultipleConfigs() = runTest {
        widgetConfigDao.insert(WidgetConfigEntity(widgetId = 1, nodeId = "node-1"))
        widgetConfigDao.insert(WidgetConfigEntity(widgetId = 2, nodeId = "node-2"))
        widgetConfigDao.insert(WidgetConfigEntity(widgetId = 3, nodeId = "node-3"))

        widgetConfigDao.deleteAll(listOf(1, 3))

        assertNull(widgetConfigDao.getConfig(1))
        assertNotNull(widgetConfigDao.getConfig(2))
        assertNull(widgetConfigDao.getConfig(3))
    }

    @Test
    fun insertWithConflictReplaces() = runTest {
        widgetConfigDao.insert(WidgetConfigEntity(widgetId = 1, nodeId = "original"))
        widgetConfigDao.insert(WidgetConfigEntity(widgetId = 1, nodeId = "replacement"))

        val result = widgetConfigDao.getConfig(1)
        assertEquals("replacement", result?.nodeId)
    }

    @Test
    fun observeConfigEmitsUpdates() = runTest {
        widgetConfigDao.insert(WidgetConfigEntity(widgetId = 1, nodeId = "initial"))

        val initial = widgetConfigDao.observeConfig(1).first()
        assertEquals("initial", initial?.nodeId)

        widgetConfigDao.updateNodeId(1, "updated")

        val updated = widgetConfigDao.observeConfig(1).first()
        assertEquals("updated", updated?.nodeId)
    }

    @Test
    fun observeConfigEmitsNullForNonexistent() = runTest {
        val result = widgetConfigDao.observeConfig(999).first()
        assertNull(result)
    }

    @Test
    fun multipleWidgetsCanReferenceTheSameNode() = runTest {
        val sharedNodeId = "shared-node"

        widgetConfigDao.insert(WidgetConfigEntity(widgetId = 1, nodeId = sharedNodeId))
        widgetConfigDao.insert(WidgetConfigEntity(widgetId = 2, nodeId = sharedNodeId))
        widgetConfigDao.insert(WidgetConfigEntity(widgetId = 3, nodeId = sharedNodeId))

        val allConfigs = widgetConfigDao.getAllConfigs()
        assertEquals(3, allConfigs.size)
        assertTrue(allConfigs.all { it.nodeId == sharedNodeId })

        val nodeIds = widgetConfigDao.getAllNodeIds()
        assertEquals(1, nodeIds.size)
        assertEquals(sharedNodeId, nodeIds[0])
    }

    @Test
    fun deleteAllWithEmptyListDoesNothing() = runTest {
        widgetConfigDao.insert(WidgetConfigEntity(widgetId = 1, nodeId = "node-1"))

        widgetConfigDao.deleteAll(emptyList())

        assertNotNull(widgetConfigDao.getConfig(1))
    }
}
