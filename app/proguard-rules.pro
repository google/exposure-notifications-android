# Copyright 2020 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# This allows proguard to strip isLoggable() blocks containing only <=INFO log
# code from release builds.
-assumenosideeffects class android.util.Log {
  static *** d(...);
  static *** v(...);
  static *** isLoggable(...);
}

-dontwarn android.support.annotation.**

-keep class androidx.core.app.CoreComponentFactory { *; }

# Room configuration.
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keepclassmembers public class * extends androidx.lifecycle.AndroidViewModel { public <init>(...); }

# Guava configuration.
-dontwarn com.google.errorprone.**
-dontwarn sun.misc.Unsafe
-dontwarn java.lang.ClassValue

# AutoValue configuration.
-keep class * extends com.google.auto
-dontwarn com.google.auto.**

# Storage
-dontwarn java.nio.ByteBuffer

# BLE Configuration constants
-keep class com.google.android.apps.exposurenotification.config.** { *; }

# Volley.
-dontwarn org.apache.http.**
-dontwarn android.net.http.**
-dontwarn com.android.volley.**

# GMSCore
-keep class org.checkerframework.checker.nullness.qual.** { *; }
-dontwarn org.checkerframework.checker.nullness.qual.**

# Joda
-dontwarn org.joda.convert.**