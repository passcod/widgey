package com.widgey.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.widgey.data.entity.NodeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NodeDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var nodeDao: NodeDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        nodeDao = database.nodeDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveNode() = runTest {
        val node = NodeEntity(
            id = "test-id-1",
            name = "Test Node",
            note = "Test note content",
            parentId = null,
            priority = 100,
            remoteModifiedAt = 1000L,
            localModifiedAt = null,
            isDirty = false
        )

        nodeDao.insert(node)
        val retrieved = nodeDao.getById("test-id-1")

        assertNotNull(retrieved)
        assertEquals("test-id-1", retrieved?.id)
        assertEquals("Test Node", retrieved?.name)
        assertEquals("Test note content", retrieved?.note)
        assertEquals(100, retrieved?.priority)
        assertFalse(retrieved?.isDirty ?: true)
    }

    @Test
    fun getByIdReturnsNullForNonexistent() = runTest {
        val result = nodeDao.getById("nonexistent-id")
        assertNull(result)
    }

    @Test
    fun getTopLevelNodesReturnsOnlyRootNodes() = runTest {
        val rootNode1 = createNode("root1", "Root 1", parentId = null, priority = 100)
        val rootNode2 = createNode("root2", "Root 2", parentId = null, priority = 200)
        val childNode = createNode("child1", "Child 1", parentId = "root1", priority = 100)

        nodeDao.insertAll(listOf(rootNode1, rootNode2, childNode))

        val topLevel = nodeDao.getTopLevelNodes()

        assertEquals(2, topLevel.size)
        assertTrue(topLevel.all { it.parentId == null })
        assertEquals("root1", topLevel[0].id) // Sorted by priority
        assertEquals("root2", topLevel[1].id)
    }

    @Test
    fun getChildNodesReturnsCorrectChildren() = runTest {
        val parent = createNode("parent", "Parent", parentId = null)
        val child1 = createNode("child1", "Child 1", parentId = "parent", priority = 100)
        val child2 = createNode("child2", "Child 2", parentId = "parent", priority = 200)
        val unrelated = createNode("other", "Other", parentId = null)

        nodeDao.insertAll(listOf(parent, child1, child2, unrelated))

        val children = nodeDao.getChildNodes("parent")

        assertEquals(2, children.size)
        assertTrue(children.all { it.parentId == "parent" })
    }

    @Test
    fun updateNoteLocallySetsDirtyFlag() = runTest {
        val node = createNode("test", "Test", note = "Original note")
        nodeDao.insert(node)

        val updateTime = System.currentTimeMillis()
        nodeDao.updateNoteLocally("test", "Updated note", updateTime)

        val updated = nodeDao.getById("test")
        assertEquals("Updated note", updated?.note)
        assertTrue(updated?.isDirty ?: false)
        assertEquals(updateTime, updated?.localModifiedAt)
    }

    @Test
    fun updateFromRemoteOnlyUpdatesNonDirtyNodes() = runTest {
        val dirtyNode = createNode("dirty", "Dirty", note = "Local changes", isDirty = true)
        val cleanNode = createNode("clean", "Clean", note = "Old content", isDirty = false)

        nodeDao.insertAll(listOf(dirtyNode, cleanNode))

        // Try to update both from remote
        nodeDao.updateFromRemote("dirty", "Remote content", 2000L)
        nodeDao.updateFromRemote("clean", "Remote content", 2000L)

        // Dirty node should NOT be updated
        val dirtyResult = nodeDao.getById("dirty")
        assertEquals("Local changes", dirtyResult?.note)

        // Clean node SHOULD be updated
        val cleanResult = nodeDao.getById("clean")
        assertEquals("Remote content", cleanResult?.note)
        assertEquals(2000L, cleanResult?.remoteModifiedAt)
    }

    @Test
    fun markSyncedClearsDirtyFlag() = runTest {
        val node = createNode("test", "Test", isDirty = true, localModifiedAt = 1000L)
        nodeDao.insert(node)

        nodeDao.markSynced("test", 2000L)

        val result = nodeDao.getById("test")
        assertFalse(result?.isDirty ?: true)
        assertEquals(2000L, result?.remoteModifiedAt)
    }

    @Test
    fun getDirtyNodesReturnsOnlyDirty() = runTest {
        val dirty1 = createNode("dirty1", "Dirty 1", isDirty = true)
        val dirty2 = createNode("dirty2", "Dirty 2", isDirty = true)
        val clean = createNode("clean", "Clean", isDirty = false)

        nodeDao.insertAll(listOf(dirty1, dirty2, clean))

        val dirtyNodes = nodeDao.getDirtyNodes()

        assertEquals(2, dirtyNodes.size)
        assertTrue(dirtyNodes.all { it.isDirty })
    }

    @Test
    fun searchByNameFindsMatches() = runTest {
        val node1 = createNode("1", "Shopping List")
        val node2 = createNode("2", "Work Tasks")
        val node3 = createNode("3", "Shopping Budget")

        nodeDao.insertAll(listOf(node1, node2, node3))

        val results = nodeDao.searchByName("Shopping")

        assertEquals(2, results.size)
        assertTrue(results.any { it.id == "1" })
        assertTrue(results.any { it.id == "3" })
    }

    @Test
    fun existsReturnsTrueForExistingNode() = runTest {
        val node = createNode("exists", "Exists")
        nodeDao.insert(node)

        assertTrue(nodeDao.exists("exists"))
        assertFalse(nodeDao.exists("nonexistent"))
    }

    @Test
    fun deleteByIdRemovesNode() = runTest {
        val node = createNode("to-delete", "Delete Me")
        nodeDao.insert(node)

        assertTrue(nodeDao.exists("to-delete"))

        nodeDao.deleteById("to-delete")

        assertFalse(nodeDao.exists("to-delete"))
    }

    @Test
    fun observeByIdEmitsUpdates() = runTest {
        val node = createNode("observed", "Original")
        nodeDao.insert(node)

        // Get initial value
        val initial = nodeDao.observeById("observed").first()
        assertEquals("Original", initial?.name)

        // Update and observe change
        nodeDao.insert(node.copy(name = "Updated"))
        val updated = nodeDao.observeById("observed").first()
        assertEquals("Updated", updated?.name)
    }

    @Test
    fun insertWithConflictReplaces() = runTest {
        val original = createNode("same-id", "Original", note = "Original note")
        nodeDao.insert(original)

        val replacement = createNode("same-id", "Replacement", note = "New note")
        nodeDao.insert(replacement)

        val result = nodeDao.getById("same-id")
        assertEquals("Replacement", result?.name)
        assertEquals("New note", result?.note)
    }

    @Test
    fun deleteAllClearsTable() = runTest {
        nodeDao.insertAll(
            listOf(
                createNode("1", "One"),
                createNode("2", "Two"),
                createNode("3", "Three")
            )
        )

        assertEquals(3, nodeDao.getTopLevelNodes().size)

        nodeDao.deleteAll()

        assertEquals(0, nodeDao.getTopLevelNodes().size)
    }

    private fun createNode(
        id: String,
        name: String,
        note: String? = null,
        parentId: String? = null,
        priority: Int = 0,
        remoteModifiedAt: Long = 0,
        localModifiedAt: Long? = null,
        isDirty: Boolean = false
    ) = NodeEntity(
        id = id,
        name = name,
        note = note,
        parentId = parentId,
        priority = priority,
        remoteModifiedAt = remoteModifiedAt,
        localModifiedAt = localModifiedAt,
        isDirty = isDirty
    )
}
