# Widgey

A lightweight Android widget for viewing and editing Workflowy notes.

## Features

- **Offline-first**: All reads and writes operate on local cache instantly. Network sync happens transparently in the background.
- **Multiple widgets**: Place as many widgets as you want, each linked to a different Workflowy node.
- **Resizable**: Widgets can be resized to any dimension on your home screen.
- **Simple theming**: Post-it yellow aesthetic that follows system light/dark mode.

## How It Works

1. **Add a widget** to your home screen
2. **Enter your Workflowy API key** (first time only, get it from [workflowy.com](https://workflowy.com/api-reference/))
3. **Select a node** from your top-level items, create a new one, or enter a node ID directly
4. **View the note** content right on your home screen
5. **Tap to edit** - opens a full-screen editor with auto-save

## Sync Behavior

- **Push**: Edits sync immediately when you make them
- **Pull**: Content refreshes every 60 seconds (debounced)
- **Offline**: Changes queue up and retry with exponential backoff (1s → 15min)
- **Conflicts**: Your local edits take precedence if they're more recent than server changes
- **Network changes**: Queued syncs retry immediately when connectivity is restored

## Visual Design

| Mode | Background | Text |
|------|------------|------|
| Light | Post-it Yellow | Black |
| Dark | Black | Post-it Yellow |

## Technical Details

- **Minimum Android version**: 12 (API 31)
- **Architecture**: Native Kotlin, Android Views (no Compose), Room database, WorkManager for background sync
- **Dependencies**: Minimal - OkHttp, Room, WorkManager, kotlinx.serialization

## Building

```
./gradlew assembleDebug
```

## License

No copyright (AI/machine output).
