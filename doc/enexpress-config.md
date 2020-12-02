# Exposure Notifications Express (EN Express) Configuration

## Overview

EN Express apps can be configured by providing a JSON configuration
file with the following format.

```
{
  "config": {
    FIELDS BELOW...
  }
}
```

Each `name.json` config file placed in the `app/config/` directory will add a Gradle build flavor, introducing additional tasks:
   * assemble*Name* - build an APK for this config.
   * bundle*Name* - build an AAB for this config.
   * publishListing*Name* - push a listing to the Play Store.
   * publishApp*Name* - push an app bundle to the Play Store.
   * publishConsent*Name* - create a `consent.json` with consent fields.

## Android Specific Fields

Due to platform differences, there are several fields only relevant to Android.
Most will be available in Apple Business Registry (ABR), except where noted.

### Play Store Related

These fields influence how the EN Express app will appear in Play Store.

#### playStoreAppTitle

**ABR Field:** (tentative: Play Store app title)\
**Data Type:** String\
**Limit:** 50 chars\
**Localizable**

Your app's name on Google Play.
Recommended 30 or less.

*Optional field, defaults to `appTitle` if blank.*

#### playStoreShortDescription

**ABR Field:** (tentative: Play Store short description)\
**Data Type:** String\
**Limit:** 80 chars\
**Localizable**

Short description of the app for the Play Store. The first text users see when
looking at your app's detail page on the Play Store app.

Users can expand this text to view your app's full description.

*Optional field, defaults to `agencyDisplayName` if blank.*

#### playStoreFullDescription

**ABR Field:** (tentative: Play Store full description)\
**Data Type:** String\
**Limit:** 4000 chars\
**Localizable**

Your app's description on Google Play.
Users can expand this text to view your app's full description.

*Optional field, defaults to `agencyDisplayName` if blank.*

#### playStoreAppIcon

**ABR Field:** (tenatative: Play Store icon)\
**Data Type:** URL

URL for the Play Store [Hi-Res Icon](https://support.google.com/googleplay/android-developer/answer/1078870?hl=en&ref_topic=3450987).

Must in PNG format.
512 x 512 pixel

Used for store listing.

*Optional field, defaults to app icon if blank.*

#### agencyPrivacyPolicy

**ABR Field:** (tentative: Privacy Policy)\
**Data Type:** URL

Link to your app's Privacy Policy.

The privacy policy must, together with any in-app disclosures,
comprehensively disclose how your app collects, uses, and shares
user data, including the types of parties with whom it’s shared.
You should consult your own legal representative to advise you of
what is required.

#### playStoreEmailContact

**ABR Field:** (tentative: Admin contact for Play Store)\
**Data Type:** String

Administrative contact for Play Store.
This contact email address will be publicly displayed as part of the
Play Store listing.

#### playStoreDefaultLocale

**ABR Field:** (tentative: Default locale for Play Store)\
**Data Type:** String

Default locale for Play Store listing.

*Optional, if absent, will auto-select based on provided locales.*

### Android App Specific

#### appTitle

**ABR Field:** Title for the PHA app.\
**Data Type:** String\
**Limit:**  50 chars
**Localizable**

Title for the PHA app.
Recommended 30 or less.
As the app title may be clipped when long, 12 characters or less for visibility.

Optional field, defaults to `agencyDisplayName` if blank

#### appLinkHost

**ABR Field:** (unknown)\
**Data Type:** String\

Hostname to use to create an
[App Link](https://developer.android.com/training/app-links)
association for SMS redirection.

**Required Field**

#### agencyIconForeground

**ABR Field:** URL for foreground portion of icon.\
**Data Type:** URL

URL for the foreground portion of an
[Android Adaptive Icon](https://developer.android.com/guide/practices/ui_guidelines/icon_design_adaptive).

Must be in either SVG or PNG.
SVG Recommended.

Should be sized to be laid out at 108dp x 108dp with the center 72dp x 72dp
region visible with either a square, round, or other adaptive stencils. May
require padding with a transparent reference square.

*Optional field, defaults to automatic layout of `agencyImage` if blank.*

#### agencyIconBackground

**ABR Field:** URL for background portion of icon.\
**Data Type:** URL

URL for the background portion of an
[Android Adaptive Icon](https://developer.android.com/guide/practices/ui_guidelines/icon_design_adaptive).

Must be in either SVG or PNG.
SVG Recommended.

Should be sized to be laid out at 108dp x 108dp with the center 72dp x 72dp
region visible with either a square, round, or other adaptive stencils. May
require padding with a transparent reference square.

*Optional field, defaults to solid `agencyColor` background if blank.*

#### tekRoamingURLs

**ABR Field:** (unavailable)\
**Data Type:** URL

Mapping from ISO-3166-1 to an additional list of base and index URLs for
download when the user has roamed to that country. Includes base and index
URLs. Indexes are de-duped before download.

Typically provided by your roaming federation.

Example:
```
{
  "US": [
   {"index": "https://my-key-server/exposureKeyExport-US/index.txt"
                     "base": "https://my-key-serrver/exposureKeyExport-US"},
   {"index": "https://my-key-server/exposureKeyExport-TRAVEL/index.txt"
                     "base": "https://my-key-server/exposureKeyExport-TRAVEL"},
           ],
  "DE": [
   {"index": "https://my-key-server/exposureKeyExport-DE/index.txt"
                     "base": "https://my-key-server/exposureKeyExport-DE"},
   {"index": "https://my-key-server/exposureKeyExport-TRAVEL/index.txt"
                     "base": "https://my-key-server/exposureKeyExport-TRAVEL"},
        ],
}
```

*Optional field, defaults to no roaming if blank.*

## ENPA Specific Fields

Exposure Notifications Private Analytics (ENPA) has some additional
configuration fields.

#### enableENPA

**ABR Field:** (unknown)\
**Data Type:** Boolean\

This field is true to enable ENPA.

#### enpaConsentText

**ABR Field:** ENPA Consent Text\
**Data Type:** String\
**Required**\
**Localizable**

A string that contains the plain text end-user consent document provided by
the PHA for ENPA.

#### enpaConsentVersion

**ABR Field:** ENPA Consent Version\
**Data Type:** String\
**Limit:** 25 chars\
**Required**\
**Localizable**

A string that contains the semantic version or monotonically increasing number
of the consent text for ENPA.

## General Fields

These fields are relevant both to Android and iOS.

### Health Authority Identification

#### healthAuthorityID

**ABR Field:** (tenatative) \
**Data Type:** String\
**Limit:** 256 chars\
**Required**

String used to identify the PHA to the upload key server.

On Android, this also sets the app package name.
Once set, it cannot be changed.

#### agencyDisplayName

**ABR Field:** PHA Name\
**Data Type:** String\
**Limit:** 50 chars\
**Required**\
**Localizable**

Name of the PHA. Used in the colored header during onboarding, in EN settings
and in Exposure Notifications details.

#### agencyRegionName

**ABR Field:** Region\
**Data Type:** String\
**Limit:** 50 chars\
**Required**\
**Localizable**

Name of the region the PHA manages. Used as the subtitle in the onboarding
header and in various places in Settings. PHA must provide this field.

#### agencyWebsiteURL

**ABR Field:** Agency Website URL\
**Data Type:** URL\
**Limit:** 500 chars

The URL for the agency's website. Presented to the user in the region’s detail
view in Settings.

#### agencyHeaderStyle

**ABR Field:** Header Style\
**Data Type:** UInt8\
**Default:** 0

An integer used to customize the appearance of the header used in Exposure
Notifications Express when presenting region-specific information to the user.
Acceptable values are: 0 for standard appearance with name and icon, 1 for
icon-only centered, 2 for icon-only on the left, and 3 for icon-only on the
right.

#### agencyHeaderTextColor

**ABR Field:** Text Color\
**Data Type:** [Float]\
**Default:** [1.0, 1.0, 1.0]

An array of RGB values ranging from 0.0 to 1.0 representing the text color for the text in the header (for example, [1.0, 0.75, 0.75]).

#### agencyColor

**ABR Field:** Header Color\
**Data Type:** [Float]\
**Default:** [0.0, 0.0, 0.0]

An array of RGB values ranging from 0.0 to 1.0 representing the accent color to
use as the background for the PHA-branded header above PHA-provided content
(for example, [1.0, 1.0, 0.0]).

#### agencyImage

**ABR Field:** Image URL\
**Data Type:** URL/String

The URL of an image file for the agency logo, displayed in the PHA-branded
header above PHA-provided content.

#### agencyMessage

**ABR Field:** Message Body\
**Data Type:** String\
**Limit:** 3000 chars\
**Required**\
**Localizable**

Summary text that appears during onboarding and in settings below the branded
header.

#### regionIdentifier

**ABR Field:** MCC Code in v1, ISO Country Code in v2\
**Data Type:** String\
**Default:** N/A

Either the [ISO-3166-1](https://en.wikipedia.org/wiki/ISO_3166-1)
two letter country code,
or the [ISO-3166-1 alpha-2](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2)
country + state code.

Example:
   * US-CA (United States, California)
   * DE (Germany)

*MCC Code in v1, Apple only*

### Legal Consent

#### legalConsentText

**ABR Field:** Legal Consent Text\
**Data Type:** String\
**Required**\
**Localizable**

A string that contains the plain text end-user consent document provided by
the PHA.

#### legalConsentVersion

**ABR Field:** Legal Consent Version\
**Data Type:** String\
**Limit:** 25 chars\
**Required**\
**Localizable**

A string that contains the semantic version or monotonically increasing number of the consent text.

### Risk Score -- Overview

The risk score for a given exposure is computed using:

*ERV = (Attenuation Weight) x (Infectiousness Weight) x (Report Type Weight)*

It combines the seconds below.

### Risk Score -- Attenuation Weight

![Risk Score Buckets](attenuation.png)

#### attenuationImmediateWeight

**ABR Field:** Attenuation Immediate Weight (0-250%)\
**Data Type:** Uint16\
**Range:** 0 - 250\
**Default:** 150

IMMEDIATE attenuation bucket weight.

#### attenuationNearWeight

**ABR Field:** Attenuation Near Weight (0-250%)\
**Data Type:** Uint16\
**Range:** 0 - 250\
**Default:** 100

NEAR attenuation bucket weight.

#### attenuationMedWeight

**ABR Field:** Attenuation Medium Weight (0-250%)\
**Data Type:** Uint16\
**Range:** 0 - 250\
**Default:** 50

MEDIUM attenuation bucket weight.

#### attenuationOtherWeight

**ABR Field:** Attenuation Other Weight (0-250%)\
**Data Type:** Uint16\
**Range:** 0 - 250\
**Default:** 0

OTHER attenuation bucket weight.

#### attenuationImmediateNearThreshold

**ABR Field:** Attenuation Immediate Near Threshold (0-255)\
**Data Type:** Uint8\
**Range:** 0 - 255\
**Default:** 30

IMMEDIATE-NEAR attenuation threshold.

#### attenuationNearMedThreshold

**ABR Field:** Attenuation Immediate Med Threshold (0-255)\
**Data Type:** Uint8\
**Range:** 0 - 255\
**Default:** 50

NEAR-MEDIUM attenuation threshold.

#### attenuationMedFarThreshold

**ABR Field:** Attenuation Med Far Threshold (0-255)\
**Data Type:** Uint8\
**Range:** 0 - 255\
**Default:** 60

MEDIUM-FAR (OTHER) attenuation threshold.

### Risk Score -- Infectiousness Weight

![Infectiousness Curve](onset.png)

#### symptomOnsetToInfectiousnessMap

**ABR Field:** Infectiousness to Symptom Onset Map\
**Data Type:** Uint64\
**Range:** 0 - 0xFFFFFFFF\
**Default:** 0x0401555AA9400000

Map from days-since-symptoms-onset in TEK to infectiousness bucket.

Three possible values per entry (in binary):
   * 00 - Drop
   * 01 - Standard infectiousness (`infectiousnessStandardWeight`)
   * 10 - High infectiousness (`infectiousnessHighWeight`)

Stored Least Significant Bit first: *-14,-13 …. 13, 14 / None*.

**NOTE: Currently this is stored inside a string to ensure Uint64 size.**

#### infectiousnessStandardWeight

**ABR Field:** Infectiousness Standard Weight (0-250%)\
**Data Type:** Uint16\
**Range:** 0 - 250\
**Default:** 100

Weight value for onset dates deemed normal risk.

#### infectiousnessHighWeight

**ABR Field:** Infectiousness High Weight (0-250%)\
**Data Type:** Uint16\
**Range:** 0 - 250\
**Default:** 100

Weight value for onset dates deemed high risk.

### Risk Score -- Report Type Weight

#### reportTypeConfirmedTestWeight

**ABR Field:** Report Type Confirmed Test Weight (0-250%)\
**Data Type:** Uint16\
**Range:** 0 - 250\
**Default:** 100

Weight to apply to report type CONFIRMED_TEST.

### Risk Score -- Report Type Weights

#### reportTypeConfirmedClinicalDiagnosisWeight

**ABR Field:** Report Type Confirmed Clinical Diagnosis Weight (0-250%) \
**Data Type:** Uint16\
**Range:** 0 - 250\
**Default:** 100

Weight to apply to report type CLINICAL_DIAGNOSIS.

#### reportTypeSelfReportWeight

**ABR Field:** Report Type Self Report Weight (0-250%)\
**Data Type:** Uint16\
**Range:** 0 - 250\
**Default:** 100

Weight to apply to report type SELF_REPORT.

#### reportTypeNoneMap

**ABR Field:** Report Type None Map\
**Data Type:** Uint8\
**Range:** 0 - 3\
**Default:** 3

This value selects what to map report types to that don't have
a report type attached:

   * 0 = DROP
   * 1 = CONFIRMED_TEST
   * 2 = CLINICAL_DIAGNOSIS
   * 3 = SELF_REPORT

### Risk Score -- General Thresholds

#### daysSinceExposureThreshold

**ABR Field:** Days Since Exposure Threshold (0-14)\
**Data Type:** Uint8\
**Range:** 0 - 14\
**Default:** 14

Limit of how many days of exposure will be considered when evaluating the
user’s total exposure.

For example, 10 means only the past 10 days of history is considered.

### Exposure Classifications

These keys define values that set the thresholds and messaging
for up to 4 exposure classifications (numbered 1-4).
Classifications with the lowest number have the highest priority,
if multiple classifications apply to a user.

Replace the trailing X in the key name with 1-4 for each classification.

#### classificationName_X

**ABR Field:** Classification Name\
**Data Type:** String\
**Limit:** 25 chars

Unique name of this classification configuration.
This name must change when the thresholds for this classification change.

#### exposureDetailsBodyText_X

**ABR Field:** Exposure Details Body\
**Data Type:** String\
**Limit:** 1500 chars\
**Localizable**

Body text presented to user when navigating to their latest exposure details in
Settings.

#### notificationSubject_X

**ABR Field:** Notification Subject\
**Data Type:** String\
**Limit:** 50 chars\
**Localizable**

Bolded subject of the notification presented to user when transitioning to this
exposure state.
Also presented as the heading in Settings when a user browses to
their latest exposure information. This field is required.

#### notificationBody_X

**ABR Field:** Notification Body\
**Data Type:** String\
**Limit:** 256 chars\
**Localizable**

Body text of notification presented to user when transitioning to this exposure
state.

#### classificationURL_X

**ABR Field:** Classification URL\
**Data Type:** URL\
**Limit:** 500 chars

URL used to link to PHA website for further guidance when user navigates to
their latest exposure details in Settings.

#### confirmedTestPerDaySumERVThreshold_X

**ABR Field:** Recursive Per Day Sum ERV Threshold\
**Data Type:** Uint32\
**Range:** 0 - 0xFFFFFFFF\
**Default:** 0

Confirmed test report type threshold in ERV of when
an OS notification is raised.

0 means disables the threshold.

#### clinicalDiagnosisPerDaySumERVThreshold_X

**ABR Field:** Clinical Diagnosis Per Day Sum ERV Threshold\
**Data Type:** Uint32\
**Range:** 0 - 0xFFFFFFFF\
**Default:** 0

Clinical Diagnosis report type threshold in ERV of when an OS notification is
raised.

0 means disables the threshold.

#### selfReportPerDaySumERVThreshold_X

**ABR Field:** Self Report Per Day Sum ERV Threshold\
**Data Type:** Uint32\
**Range:** 0 - 0xFFFFFFFF\
**Default:** 0

Self report report type threshold in ERV of when an OS notification is raised.

0 means disables the threshold.

#### recursivePerDaySumERVThreshold_X

**ABR Field:** Recursive Per Day Sum ERV Threshold\
**Data Type:** Uint32\
**Range:** 0 - 0xFFFFFFFF\
**Default:** 0

Recursive report type threshold in ERV of when an OS notification is raised.

0 means disables the threshold.

#### perDaySumERVThreshold_X

**ABR Field:** Per Day Sum ERV Threshold\
**Data Type:** Uint32\
**Range:** 0 - 0xFFFFFFFF\
**Default:** 0

Per-day Sum threshold in ERV of when an OS notification is raised.

0 means disables the threshold.

#### perDayMaxERVThreshold_X

**ABR Field:** Per Day Max ERV Threshold\
**Data Type:** Uint32\
**Range:** 0 - 0xFFFFFFFF\
**Default:** 0

Per-day Max Threshold in ERV of when an OS notification is raised.

0 means disables the threshold.

#### weightedDurationAtAttenuationThreshold_X

**ABR Field:** Weighted Duration at Attenuation Threshold\
**Data Type:** Uint32\
**Range:** 0 - 0xFFFFFFFF\
**Default:** 0

The threshold in weighted duration-at-attenuation when the OS raises a
notification.

0 means disables the threshold.

### Revocation Handling

#### revokedNotificationBody

**ABR Field:** Notification Body\
**Data Type:** String\
**Limit:** 256 chars\
**Localizable**

Body text of notification presented to user when transitioning to no exposure
due to revocation of prior exposure.

#### revokedNotificationSubject

**ABR Field:** Notification Subject\
**Data Type:** String\
**Limit:** 50 chars\
**Localizable**

Bolded subject of the notification presented to user when transitioning to this
exposure state.

#### revokedDetailsBodyText

**ABR Field:** Revoked Details Body\
**Data Type:** String\
**Limit:** 1500 chars\
**Localizable**

Body text presented to user when navigating to their latest exposure details in
Settings.

#### revokedURL

**ABR Field:** Revoked URL\
**Data Type:** URL\
**Limit:** 500 chars

URL used to link to PHA website for further guidance when user navigates to
their latest exposure details in Settings.

### Verification

#### testVerificationIntroMessage

**ABR Field:** Test Verifiction Intro Message\
**Data Type:** string\
**Limit:** 1000 chars \
**Required** \
**Localizable**

Intro message from PHA explaining purpose of test verification and key upload.

#### verificationCodeLearnMoreURL

**ABR Field:** Verification Learn More URL\
**Data Type:** URL\
**Limit:** 500 chars\
**Required** \
**Localizable**

Link to the PHA’s website where user can learn more about use of Verification
Codes and get help if something isn’t working.

#### symptomsOnsetDescription

**ABR Field:** (unavailable)\
**Data Type:** string\
**Limit:** 8000 chars\
**Required** \
**Localizable**

Text explaining how users should determine if they had relevant
symptoms and how to pick an onset date.

#### traveledQuestionText

**ABR Field:** (unavailable)\
**Data Type:** string\
**Limit:** 1000 chars\
**Required** \
**Localizable**

Text asking the user if they have traveled outside the relevant region.

E.g.
Have you traveled outside the continental United States in the past 14 days?

### Server Configuration

#### tekLocalDownloadIndexFile

**ABR Field:** TEK Local Download Index File\
**Data Type:** URL

Full URL to the index file for the Key Server.

#### tekLocalDownloadBasePath

**ABR Field:** TEK Local Download Base Path\
**Data Type:** URL

Full URL used to construct path to files listed in index file,
starting with base path.

#### testVerificationURL

**ABR Field:** Test Verification URL\
**Data Type:** URL\
**Limit:** 500 chars

The URL for the agency’s Test Verification Server

#### testVerificationCertificateURL

**ABR Field:** Test Verification Certificate URL\
**Data Type:** URL\
**Limit:** 500 chars

URL for the Test Verification server token + HMAC -> certificate roundtrip

#### testVerificationAPIKey

**ABR Field:** Test Verification API Key\
**Data Type:** String\
**Limit:**  256 chars

API Key required to talk to the Test Verification server.

This can be obtained from the reference verification server admin UI.

#### tekUploadURL

**ABR Field:** TEK Upload URL\
**Data Type:** URL\
**Limit:** 500 chars

The URL for the agency's upload key server.

## iOS Fields unused by Android

#### goLiveDate

**ABR Field:** Go Live Date\
**Data Type:** UInt32\
**Default:** N/A

Go live date for when configs should start being returned for this region.

Stored as int, seconds since 1970, GMT.

**NOTE: Not currently supported on Android.**

#### exposureMatching

**ABR Field:** Is exposure matching enabled?\
**Data Type:** Boolean\
**Default:** FALSE

Whether the API’s which accept TEK files to match is enabled in that region.

#### enVersion

**ABR Field:** Determine if app is v1 or v2\
**Data Type:** Uint8

An unsigned 8-bit integer that determines whether an app uses version 1 or 2 of the Exposure Notifications framework.

**NOTE: This field is unused on Android as v1 is managed separately.**

#### publicKey

**ABR Field:** Public Key\
**Data Type:** String\
**Default:** N/A

A string that contains the public key used to verify
the Temporary Exposure Key (TEK) file signature.

*This information is communicated to Android separately.
Multiple valid keys can be simultaneously active to support
key rotation.*

#### publicKeyVersion

**ABR Field:** Public Key Version\
**Data Type:** String\
**Default:** 1

*This information is communicated to Android separately.
Multiple valid keys can be simultaneously active to support
key rotation.*

#### appBundleId

**ABR Field:** App Bundle ID\
**Data Type:** String\
**Default:** N/A

App Store bundle ID of the app for the given region.

#### tekPublishInterval

**ABR Field:** TEK Publish Interval\
**Data Type:** UInt8\
**Range:** 2 - 24\
**Default:** 24

Hours; How often the PHA expects to update TEKs for us to download. We will
attempt to download on this cadence but may go faster or slower based on device
environment. Valid values are 2, 4, 8, and 24

**NOTE: Currently on Android this value is fixed at 4 hours.**

#### isTestRegion

**ABR Field:** Determine if this is a test region\
**Data Type:** Boolean (Not editable)

A Boolean that indicates whether this is a test region.

**NOTE: Unavailable on Android**
