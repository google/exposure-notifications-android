# Exposure Notifications API: Android Reference Design

## Archive Status Announcement

Exposure Notifications is no longer available as of September 18, 2023. The Exposure Notifications project will be moving into archive status.

## Overview

This is a reference design for an Android app implementing the
[Exposure Notifications API](https://www.blog.google/inside-google/company-announcements/apple-and-google-partner-covid-19-contact-tracing-technology/)
provided by Apple and Google.

Only approved government public health authorities can access the APIs
(included in an upcoming build of `Google Play services`) to build an app for
COVID-19 response efforts. As part of our continued efforts to be transparent,
we’re making this code public to help approved government public health
authorities understand how to use the `Exposure Notifications API` to build
their COVID-19 apps.

Read the [Exposure Notifications API Terms of Service](https://google.com/covid19/exposurenotifications)

## Getting Started

This project uses the Gradle build system. To build this code, use the
`gradlew build` command or use `Import Project` in Android Studio.

## Links

- [Overview of COVID-19 Exposure Notifications](https://www.blog.google/documents/66/Overview_of_COVID-19_Contact_Tracing_Using_BLE_1.pdf)
- [Frequently Asked Questions](https://g.co/ExposureNotificationFAQ)
- [Exposure Notification Bluetooth Specification](https://www.blog.google/documents/70/Exposure_Notification_-_Bluetooth_Specification_v1.2.2.pdf)
- [Exposure Notification Cryptography Specification](https://blog.google/documents/69/Exposure_Notification_-_Cryptography_Specification_v1.2.1.pdf)
- [Android Exposure Notifications API](https://developers.google.com/android/exposure-notifications/exposure-notifications-api)
- [Exposure Key Export and File Format](https://developers.google.com/android/exposure-notifications/exposure-key-file-format)
