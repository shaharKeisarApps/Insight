---
name: android-platform-expert
description: Deep expertise in Android platform specifics for KMP projects. Use for Services, IPC, BroadcastReceivers, ContentProviders, or deep Android integration within KMP.
---

# Android Platform Expert (KMP Context)

## Overview

Even in KMP, Android-specific components often need to be implemented in `androidMain` or a specific Android module.

## When to Use

- Implementing Android Services, BroadcastReceivers, or ContentProviders in a KMP project
- Setting up platform-specific expect/actual declarations for Android
- Configuring permissions, notifications, or WorkManager from shared KMP code
- Integrating with Android lifecycle, deep links, or foreground services
- Bridging KMP common code with Android-only APIs

## Services in KMP

Define the interface in `commonMain`, implement in `androidMain`.

### Foreground Service

```kotlin
// androidMain
class AudioService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
```

### Binding to Services

Use `bindService` within an Activity or specialized Scope.

## Inter-Process Communication (IPC)

### AIDL

Define AIDL files in `src/main/aidl`.

```java
// IRemoteService.aidl
package com.example.remote;

interface IRemoteService {
    void performAction();
    int getPid();
}
```

### Messenger

Simpler IPC using `Handler`.

```kotlin
val messenger = Messenger(IncomingHandler())
return messenger.binder
```

## Integration with Compose

### AndroidView

Use `AndroidView` to wrap legacy Views or platform-specific widgets (like MapView, SurfaceView).

```kotlin
AndroidView(
    factory = { context ->
        MapView(context).apply {
            onCreate(null)
        }
    },
    update = { view ->
        // Update view properties
    }
)
```

### Context Awareness

Access Context via `LocalContext.current` in Composable. In Metro DI graphs, provide the `Context` as a binding:

```kotlin
@DependencyGraph
interface AndroidAppGraph {
    @Provides
    fun provideContext(application: Application): Context = application.applicationContext
}
```

> **Note**: This project uses Metro DI, not Hilt. Do NOT use `@ApplicationContext` (Hilt annotation).

## BroadcastReceivers

Register dynamically to avoid Manifest limitations (for some broadcasts).

```kotlin
val receiver = remember { MyReceiver() }
DisposableEffect(Unit) {
    val filter = IntentFilter(Intent.ACTION_POWER_CONNECTED)
    context.registerReceiver(receiver, filter)
    onDispose {
        context.unregisterReceiver(receiver)
    }
}
```
