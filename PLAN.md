# Widgey Implementation Plan

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         Android System                          │
├─────────────────────────────────────────────────────────────────┤
│  Home Screen Widget    │    EditorActivity    │   Config Flow   │
│  (RemoteViews)         │    (Edit note)       │   (Select node) │
├─────────────────────────────────────────────────────────────────┤
│                        Sync Engine                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │ SyncManager │  │ SyncWorker  │  │ PeriodicSyncWorker      │  │
│  │ (push/pull) │  │ (retries)   │  │ (60s interval)          │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
│                              │                                  │
│                    ┌─────────┴─────────┐                        │
│                    │  NetworkMonitor   │                        │
│                    │  (connectivity)   │                        │
│                    └───────────────────┘                        │
├─────────────────────────────────────────────────────────────────┤
│                       Data Layer                                │
│  ┌─────────────────────────┐    ┌─────────────────────────────┐ │
│  │     NodeRepository      │    │    SettingsRepository       │ │
│  └───────────┬─────────────┘    └─────────────────────────────┘ │
│              │                                                  │
│  ┌───────────┴───────────┐    ┌─────────────────────────────┐   │
│  │  Room Database        │    │  WorkflowyApi (OkHttp)      │   │
│  │  - nodes              │    │  - GET/POST /api/v1/nodes   │   │
│  │  - widget_config      │    │  - GET /api/v1/targets      │   │
│  │  - sync_queue         │    └─────────────────────────────┘   │
│  │  - settings           │                                      │
│  └───────────────────────┘                                      │
└─────────────────────────────────────────────────────────────────┘
```

## Database Schema

### nodes
| Column | Type | Description |
|--------|------|-------------|
| id | TEXT PK | UUID from Workflowy |
| name | TEXT | Node title (for display in selection list) |
| note | TEXT? | Node note content (what we display/edit) |
| parent_id | TEXT? | Parent node ID (null = top-level) |
| priority | INT | Sort order among siblings |
| remote_modified_at | LONG | Server's `modifiedAt` timestamp |
| local_modified_at | LONG? | When user last edited locally (null = no local edits) |
| is_dirty | BOOL | Has unsynced local changes |

### widget_config
| Column | Type | Description |
|--------|------|-------------|
| widget_id | INT PK | Android's appWidgetId |
| node_id | TEXT? | Which node this widget displays (null = unconfigured/discarded) |

### sync_queue
| Column | Type | Description |
|--------|------|-------------|
| id | INT PK | Auto-increment |
| node_id | TEXT | Node to push |
| created_at | LONG | When queued |
| retry_count | INT | Number of failed attempts |
| next_retry_at | LONG | When to retry next (epoch ms) |

### settings
| Column | Type | Description |
|--------|------|-------------|
| key | TEXT PK | Setting name |
| value | TEXT? | Setting value |

Keys: `api_key`

## Sync Logic

### Push (on user edit)
1. Update local DB: set `note`, `is_dirty = true`, `local_modified_at = now()`
2. Insert into `sync_queue` (or update existing entry for this node)
3. Trigger immediate sync attempt via WorkManager
4. On success: clear `is_dirty`, remove from queue
5. On failure: increment `retry_count`, set `next_retry_at` with backoff

### Pull (every 60 seconds)
1. Skip if last pull was < 60 seconds ago (debounce)
2. For each node in `widget_config`:
   - `GET /api/v1/nodes/:id`
   - If 404: mark node as deleted (handle in editor)
   - Compare `remote.modifiedAt` with stored `remote_modified_at`
   - If remote is newer AND NOT `is_dirty`: update local from remote
   - If remote is newer AND `is_dirty`: keep local (user's edit wins)
   - Update `remote_modified_at`
3. Refresh affected widgets

### Retry Backoff
```
delay = min(15 minutes, 1 second * 2^retry_count)
```
- 1s → 2s → 4s → 8s → 16s → 32s → 64s → 128s → 256s → 512s → 900s (capped)

### Network Change
- On connectivity gained: reset `next_retry_at = now()` for all queued items, trigger sync

## User Flows

### Widget Creation
```
User adds widget
       │
       ▼
WidgetConfigActivity
       │
       ▼
API key set? ──No──► ApiKeyActivity ──► Validate key
       │                                      │
      Yes◄────────────────────────────────────┘
       │
       ▼
NodeSelectionActivity
       │
       ├── Select existing top-level node
       ├── Create new top-level node
       └── Enter node ID manually
       │
       ▼
Save widget_config (widget_id, node_id)
       │
       ▼
Refresh widget with node content
```

### Tap Widget (Configured)
```
User taps widget
       │
       ▼
EditorActivity (with node_id)
       │
       ▼
Node exists? ──No──► Show "deleted" dialog
       │                    │
      Yes                   ├── "Re-create at root" → Create node, update config
       │                    └── "Discard" → Clear node_id from config
       ▼
Show editor with note content
       │
       ▼
User edits (auto-save debounced 500ms)
       │
       ▼
Save to local DB + queue sync
```

### Tap Widget (Unconfigured/Discarded)
```
User taps empty widget
       │
       ▼
NodeSelectionActivity (reconfigure mode)
       │
       ▼
Select/create node
       │
       ▼
Update widget_config
       │
       ▼
Refresh widget
```

### API Key Reset
```
User opens ApiKeyActivity (from app launcher or widget long-press)
       │
       ▼
Clear existing key
       │
       ▼
All widgets show error state
       │
       ▼
Enter new key → Validate → Save
       │
       ▼
All widgets recover
```

## File Structure

```
widgey/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── kotlin/com/widgey/
│       │   ├── WidgeyApp.kt                    # Application class
│       │   │
│       │   ├── data/
│       │   │   ├── db/
│       │   │   │   ├── AppDatabase.kt
│       │   │   │   ├── NodeDao.kt
│       │   │   │   ├── WidgetConfigDao.kt
│       │   │   │   ├── SyncQueueDao.kt
│       │   │   │   └── SettingsDao.kt
│       │   │   ├── entity/
│       │   │   │   ├── NodeEntity.kt
│       │   │   │   ├── WidgetConfigEntity.kt
│       │   │   │   ├── SyncQueueEntity.kt
│       │   │   │   └── SettingEntity.kt
│       │   │   ├── api/
│       │   │   │   ├── WorkflowyApi.kt
│       │   │   │   └── dto/
│       │   │   │       ├── NodeDto.kt
│       │   │   │       └── ApiResponses.kt
│       │   │   └── repository/
│       │   │       ├── NodeRepository.kt
│       │   │       └── SettingsRepository.kt
│       │   │
│       │   ├── sync/
│       │   │   ├── SyncManager.kt
│       │   │   ├── SyncWorker.kt
│       │   │   ├── PeriodicSyncWorker.kt
│       │   │   └── NetworkMonitor.kt
│       │   │
│       │   ├── widget/
│       │   │   ├── WidgeyProvider.kt
│       │   │   ├── WidgetConfigActivity.kt
│       │   │   └── WidgetUpdater.kt
│       │   │
│       │   └── ui/
│       │       ├── editor/
│       │       │   ├── EditorActivity.kt
│       │       │   └── EditorViewModel.kt
│       │       ├── nodeselection/
│       │       │   ├── NodeSelectionActivity.kt
│       │       │   └── NodeListAdapter.kt
│       │       └── apikey/
│       │           └── ApiKeyActivity.kt
│       │
│       └── res/
│           ├── drawable/
│           │   └── widget_preview.xml
│           ├── layout/
│           │   ├── widget_layout.xml
│           │   ├── activity_editor.xml
│           │   ├── activity_node_selection.xml
│           │   ├── activity_api_key.xml
│           │   ├── item_node.xml
│           │   └── dialog_create_node.xml
│           ├── xml/
│           │   ├── widget_info.xml
│           │   └── backup_rules.xml
│           ├── mipmap-anydpi-v26/
│           │   └── ic_launcher.xml
│           ├── values/
│           │   ├── colors.xml
│           │   ├── strings.xml
│           │   ├── themes.xml
│           │   └── dimens.xml
│           └── values-night/
│               └── themes.xml
│
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── README.md
└── PLAN.md
```

## Dependencies

```kotlin
dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // OkHttp
    implementation("okhttp:okhttp:4.12.0")
    
    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    
    // Material Components (for dialogs, buttons)
    implementation("com.google.android.material:material:1.11.0")
}
```

## Implementation Phases

### Phase 1: Project Foundation ✅
- [x] Project setup with Gradle (Kotlin DSL)
- [x] AndroidManifest with permissions and components
- [x] Room database with all entities and DAOs
- [x] WorkflowyApi client with OkHttp
- [x] SettingsRepository for API key storage
- [x] Colors, themes, and basic resources

### Phase 2: Widget Basics ✅
- [x] WidgeyProvider (AppWidgetProvider)
- [x] widget_layout.xml with RemoteViews
- [x] widget_info.xml metadata
- [x] WidgetUpdater helper
- [x] WidgetConfigActivity skeleton
- [x] Widget preview drawable

### Phase 3: API Key Flow ✅
- [x] ApiKeyActivity UI
- [x] API key validation (test with /api/v1/targets)
- [x] Error states when key missing/invalid

### Phase 4: Node Selection ✅
- [x] NodeSelectionActivity layout
- [x] NodeListAdapter for RecyclerView
- [x] Fetch top-level nodes from API
- [x] Create new node dialog
- [x] Manual node ID entry
- [x] Integration with widget configuration

### Phase 5: Editor ✅
- [x] EditorActivity layout
- [x] EditorViewModel (simplified - logic in Activity)
- [x] Load node content from local DB
- [x] Auto-save with debounce (500ms)
- [x] Sync status indicator
- [x] Deleted node handling (re-create or discard dialog)

### Phase 6: Sync Engine ✅
- [x] SyncManager with push/pull logic
- [x] SyncWorker for push with retry backoff
- [x] PeriodicSyncWorker (60s interval)
- [x] NetworkMonitor for connectivity changes
- [x] Conflict resolution (timestamp comparison)
- [x] Widget refresh after sync

### Phase 7: Polish ✅
- [x] App icon (yellow adaptive icon)
- [x] Edge cases and error handling
- [x] ProGuard/R8 rules
- [ ] Final testing

## API Reference

### Workflowy API (https://workflowy.com/api-reference/)

**Authentication**: `Authorization: Bearer <API_KEY>`

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/v1/nodes?parent_id=None | List top-level nodes |
| GET | /api/v1/nodes/:id | Get single node |
| POST | /api/v1/nodes/:id | Update node (name, note) |
| POST | /api/v1/nodes | Create node |
| GET | /api/v1/targets | List targets (for key validation) |

### Node Object
```json
{
  "id": "6ed4b9ca-256c-bf2e-bd70-d8754237b505",
  "name": "Node title",
  "note": "Note content (what we display/edit)",
  "priority": 200,
  "data": { "layoutMode": "bullets" },
  "createdAt": 1753120779,
  "modifiedAt": 1753120850,
  "completedAt": null
}
```