package com.widgey.data.api

import com.widgey.data.api.dto.CreateNodeResponse
import com.widgey.data.api.dto.NodeDto
import com.widgey.data.api.dto.NodesListResponse
import com.widgey.data.api.dto.SingleNodeResponse
import com.widgey.data.api.dto.StatusResponse
import com.widgey.data.api.dto.TargetsResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkflowyApiTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `parse single node response`() {
        val jsonString = """
            {
              "node": {
                "id": "6ed4b9ca-256c-bf2e-bd70-d8754237b505",
                "name": "Test Node",
                "note": "This is a note",
                "priority": 200,
                "data": {
                  "layoutMode": "bullets"
                },
                "createdAt": 1753120779,
                "modifiedAt": 1753120850,
                "completedAt": null
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<SingleNodeResponse>(jsonString)
        val node = response.node

        assertEquals("6ed4b9ca-256c-bf2e-bd70-d8754237b505", node.id)
        assertEquals("Test Node", node.name)
        assertEquals("This is a note", node.note)
        assertEquals(200, node.priority)
        assertEquals(1753120779L, node.createdAt)
        assertEquals(1753120850L, node.modifiedAt)
        assertNull(node.completedAt)
    }

    @Test
    fun `parse node with null note`() {
        val jsonString = """
            {
              "node": {
                "id": "abc123",
                "name": "No Note Node",
                "note": null,
                "priority": 100,
                "createdAt": 1753120779,
                "modifiedAt": 1753120850
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<SingleNodeResponse>(jsonString)

        assertNull(response.node.note)
        assertEquals("No Note Node", response.node.name)
    }

    @Test
    fun `parse nodes list response`() {
        val jsonString = """
            {
              "nodes": [
                {
                  "id": "node1",
                  "name": "First Node",
                  "note": null,
                  "priority": 100,
                  "createdAt": 1753120787,
                  "modifiedAt": 1753120815
                },
                {
                  "id": "node2",
                  "name": "Second Node",
                  "note": "Has a note",
                  "priority": 200,
                  "createdAt": 1753120800,
                  "modifiedAt": 1753120900
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<NodesListResponse>(jsonString)

        assertEquals(2, response.nodes.size)
        assertEquals("node1", response.nodes[0].id)
        assertEquals("node2", response.nodes[1].id)
        assertEquals("Has a note", response.nodes[1].note)
    }

    @Test
    fun `parse empty nodes list`() {
        val jsonString = """{"nodes": []}"""

        val response = json.decodeFromString<NodesListResponse>(jsonString)

        assertEquals(0, response.nodes.size)
    }

    @Test
    fun `parse create node response`() {
        val jsonString = """
            {
              "item_id": "5b401959-4740-4e1a-905a-62a961daa8c9"
            }
        """.trimIndent()

        val response = json.decodeFromString<CreateNodeResponse>(jsonString)

        assertEquals("5b401959-4740-4e1a-905a-62a961daa8c9", response.itemId)
    }

    @Test
    fun `parse status response`() {
        val jsonString = """{"status": "ok"}"""

        val response = json.decodeFromString<StatusResponse>(jsonString)

        assertEquals("ok", response.status)
    }

    @Test
    fun `parse targets response`() {
        val jsonString = """
            {
              "targets": [
                {
                  "key": "home",
                  "type": "shortcut",
                  "name": "My Home Page"
                },
                {
                  "key": "inbox",
                  "type": "system",
                  "name": null
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<TargetsResponse>(jsonString)

        assertEquals(2, response.targets.size)
        assertEquals("home", response.targets[0].key)
        assertEquals("shortcut", response.targets[0].type)
        assertEquals("My Home Page", response.targets[0].name)
        assertEquals("inbox", response.targets[1].key)
        assertEquals("system", response.targets[1].type)
        assertNull(response.targets[1].name)
    }

    @Test
    fun `parse node with parent_id`() {
        val jsonString = """
            {
              "node": {
                "id": "child-node",
                "name": "Child",
                "note": null,
                "parent_id": "parent-node",
                "priority": 100,
                "createdAt": 1753120779,
                "modifiedAt": 1753120850
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<SingleNodeResponse>(jsonString)

        assertEquals("parent-node", response.node.parentId)
    }

    @Test
    fun `parse node ignores unknown fields`() {
        val jsonString = """
            {
              "node": {
                "id": "test",
                "name": "Test",
                "note": null,
                "priority": 100,
                "createdAt": 1753120779,
                "modifiedAt": 1753120850,
                "unknownField": "should be ignored",
                "anotherUnknown": 12345
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<SingleNodeResponse>(jsonString)

        assertEquals("test", response.node.id)
    }

    @Test
    fun `parse node with completedAt timestamp`() {
        val jsonString = """
            {
              "node": {
                "id": "completed-node",
                "name": "Done Task",
                "note": null,
                "priority": 100,
                "createdAt": 1753120779,
                "modifiedAt": 1753120850,
                "completedAt": 1753120900
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<SingleNodeResponse>(jsonString)

        assertEquals(1753120900L, response.node.completedAt)
    }
}
