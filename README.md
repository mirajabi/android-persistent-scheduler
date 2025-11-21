# Android Persistent Scheduler

[![](https://jitpack.io/v/mirajabi/android-persistent-scheduler.svg)](https://jitpack.io/#mirajabi/android-persistent-scheduler)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=21)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

**Android Persistent Scheduler** is a robust, battery-resilient Android library designed to keep your background tasks running reliably. It uses a unified foreground service architecture to manage multiple modules (Scheduler, HTTP Server) under a single persistent notification, minimizing system termination risks even on modern Android versions.

## 🚀 Features

*   **Persistent Foreground Service**: Keeps the app alive with a high-priority notification.
*   **Unified Architecture**: Manage multiple background modules (Scheduler, HTTP, etc.) with a single service.
*   **Built-in Scheduler**: Precise countdown timer and task execution.
*   **Embedded HTTP Server**: Lightweight HTTP server to communicate with your app via local network.
*   **Battery Optimization**: Helper utilities to request "Ignore Battery Optimizations".
*   **Watchdog Mechanism**: Redundant periodic work to restart the service if killed.
*   **Android 14+ Ready**: Compatible with the latest foreground service types (`dataSync`).

## 📋 Requirements

*   **minSdk**: 21 (Android 5.0 Lollipop)
*   **targetSdk**: 34 (Android 14)
*   **Language**: Kotlin / Java (Java 8+)

## 📦 Installation

Step 1. Add the JitPack repository to your build file.

**Gradle (Kotlin DSL) - `settings.gradle.kts`**
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Step 2. Add the dependency.

**Gradle (Kotlin DSL) - `build.gradle.kts`**
```kotlin
dependencies {
    implementation("com.github.mirajabi:android-persistent-scheduler:1.0.0")
}
```

## 🛠 Usage

### 1. Kotlin Example

Initialize and start the service in your `MainActivity` or `Application` class.

```kotlin
import com.example.scheduler.lib.core.ServiceManager
import com.example.scheduler.lib.core.SchedulerConfig
import com.example.scheduler.lib.scheduler.SchedulerModule
import com.example.scheduler.lib.http.HttpServerModule

// Configure the Scheduler
val config = SchedulerConfig(
    notificationTitle = "My App Service",
    notificationContent = "Background tasks running...",
    notificationIcon = R.drawable.ic_notification,
    enableCountdown = true,
    countdownFormat = "Next task in: %s"
)

// Add modules and start
ServiceManager.addModule(
    SchedulerModule(config)
).addModule(
    HttpServerModule(port = 8030) { request ->
        // Handle HTTP request
        "<html><body><h1>Hello from Android!</h1></body></html>"
    }
)

// Start the service
ServiceManager.start(context)
```

### 2. Java Example

The library is fully interoperable with Java.

```java
import com.example.scheduler.lib.core.ServiceManager;
import com.example.scheduler.lib.core.SchedulerConfig;
import com.example.scheduler.lib.scheduler.SchedulerModule;
import com.example.scheduler.lib.http.HttpServerModule;

// Configure
SchedulerConfig config = new SchedulerConfig(
    "My App Service",
    "Background tasks running...",
    R.drawable.ic_notification,
    true,
    "Next task in: %s"
);

// Add modules
ServiceManager.INSTANCE.addModule(new SchedulerModule(config));
ServiceManager.INSTANCE.addModule(new HttpServerModule(8030, (request) -> {
    return "<html><body><h1>Hello from Java!</h1></body></html>";
}));

// Start
ServiceManager.INSTANCE.start(context);
```

### 3. Permissions

The library automatically adds necessary permissions to your merged manifest. However, for **Android 13+**, you must request the Notification permission at runtime.

```xml
<!-- Added automatically by the library -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.INTERNET" />
```

## 📱 Suitable Projects

This library is ideal for:
*   **IoT Controllers**: Apps that need to listen for local network commands continuously.
*   **Real-time Trackers**: GPS or data loggers that must not be killed by the system.
*   **Kiosk Apps**: Always-on applications for dedicated devices.
*   **Scheduled Automation**: Apps that need to execute tasks at precise intervals without relying on unreliable `WorkManager` constraints.

## 📄 Changelog

### v1.0.0
*   Initial release.
*   CoreService architecture.
*   SchedulerModule with countdown timer.
*   HttpServerModule with dynamic response support.

## ⚖️ License

```text
MIT License

Copyright (c) 2025 Android Persistent Scheduler

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
