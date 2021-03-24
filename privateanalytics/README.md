# Private Analytics SDK: Getting started

The goal of this SDK is to use the [Prio algorithm](https://crypto.stanford.edu/prio/) on
user-derived data, so that it can be aggregated in a privacy-preserving way: no individual server to
which data is sent is able to learn anything about any specific user's data; only aggregated
results are visible.

## Concepts

- Metrics: a user-specific value that can be aggregated. In this SDK, we expect the data for a metric to be of type `List<Integer>`. You can have multiple metrics at once (with different names).
- Hamming weight, epsilon, prime: those are parameters of a metric. For more information about those, see the [Prio paper](https://crypto.stanford.edu/prio/).
- Sampling rate: if you want data from your whole population, use a sampling rate of 1. If you are only interested in a fraction of the population, you can use a sampling rate of the same value (so for half the population, put a sampling rate of 0.5).
- Prio servers: Prio maintains user privacy by sending encrypted data shares to multiple servers, which can perform aggregations separately, before sending the aggregations to a central server. Since the data shares need to be encrypted per server, each server will need its own unique public key for each server. The current implementation of the SDK expects two servers, so two public keys are required. In this SDK, we name those two servers "PHA" and "facilitator".
- Ingestion server: instead of having individual users interact with multiple servers for every share, we instead send all the (encrypted) content to an ingestion server. To maintain user privacy, the ingestion should never know the private keys associated with the encryption. The ingestion server's role is to filter out potentially fraudulent submissions, and forward valid ones to their respective Prio server.

## Integration guide

### Creating `PrioDataPoints`

First, you will need to compute the user data you want to share through Prio. As mentioned earlier, we expect this data to be of type `List<Integer>`.

From this, you will be able to create a `PrivateAnalyticsMetric` object.

```java
  List<Integer> userData = // ...
  PrioDataPoint dataPoint = new PrioDataPoint(new PrivateAnalyticsMetric() {
    @Override
    public String getMetricName() {
      return "my_metric";
    }

    @Override
    public int getMetricHammingWeight() {
      return 1;
    }

    @Override
    public void resetData() {
      // ...
    }

    @Override
    public ListenableFuture<List<Integer>> getDataVector() {
      return Futures.immediateFuture(userData);
    }
  }, /* epsilon */12.f, /* sampleRate */1.);
```

To handle multiple metrics at once, create multiple `PrioDataPoint`s. Later, we will use a `PrivateAnalyticsSubmitter` to send a `List<PrioDataPoint>`.

### PrivateAnalyticsRemoteConfig

The SDK allows you to override certain parameters at runtime, including if they are provided by a server.

Those config values are:

- whether the analytics are enabled
- the analytics collection frequency
- whether a device attestation is required
- certificates for the servers
- encryption key ids for the servers

If you are setting those values at build time, the simplest implementation would be:

```java
PrivateAnalyticsRemoteConfig remoteConfig = () -> Futures.immediateFuture(RemoteConfigs.newBuilder().build());
```

If it's relevant to your use case, we also provide a default remote config provider, which assumes you are going to need a network request to get the latest config and that this config will have the following JSON structure (the collection frequency is in number of hours):

```json
{
  "enpa_enabled": false,
  "enpa_collection_frequency": 24,
  "device_attestation_required": true,
  "certificate_pha": "...",
  "certificate_facilitator": "...",
  "encryption_key_id_pha": "...",
  "encryption_key_id_facilitator": "..."
}
```

If you have this JSON hosted at an address stored in `remoteConfigUri`, then you can create the following remote config instance:

```java
PrivateAnalyticsRemoteConfig remoteConfig = new DefaultPrivateAnalyticsRemoteConfig(remoteConfigUri, /* listener */ Optional.absent());
```

The second parameter of the constructor is an optional listener that you can set if you want a callback to report on the success or failure of the network request and JSON parsing.

### PrivateAnalyticsDeviceAttestation

To filter out potential fraudulent uploads, you can create an instance of device attestation, using your own mechanism to check whether the upload is valid. This attestation should then be attached to the payload that will be sent to the ingestion server, so that the server can decide whether to allow the contribution or not.

If you want to authorize all uploads (no filtering from the ingestion server), the simplest device attestation you can write is:

```java
  PrivateAnalyticsDeviceAttestation deviceAttestation = new PrivateAnalyticsDeviceAttestation() {
    @Override
    public boolean signPayload(String metricName, Map<String, Object> document,
        PrioAlgorithmParameters params, CreatePacketsResponse response,
        long collectionFrequencyHours) throws Exception {
      return false;
    }

    @Override
    public void clearData(List<String> listOfMetrics) {}
  };
```

To use a device attestation mechanism, without writing your own, we provide a default implementation:

```java
  PrivateAnalyticsDeviceAttestation deviceAttestation = new DefaultPrivateAnalyticsDeviceAttestation(context);
```

### PrivateAnalyticsFirestoreRepository

The current version of the SDK assumes that you will be sending the data to the ingestion server through Firestore (a service provided by [Firebase](https://firebase.google.com/docs/firestore)). This has the benefit of completely handling networking for you (waiting for network to become available, retry if necessary until the upload succeeds, and so on).
You must register your app with Firebase and initialize Firebase inside of your app (using `FirebaseApp.initializeApp(context)`).
Once you've done so, you can get a `FirebaseFirestore` instance with `FirebaseFirestore.getInstance()`.

With that, you can create a `PrivateAnalyticsFirestoreRepository` instance:
```java
  PrivateAnalyticsFirestoreRepository firestoreRepository = new PrivateAnalyticsFirestoreRepository(deviceAttestation, firebaseFirestore);
```

### PrivateAnalyticsEnabledProvider

This is a simple class that tells you whether Private Analytics are enabled. It returns two values: one enabling at the app level (for example, if you have debug and release versions of your app) and one specific to the user (for example, whether they have opted in to analytics). You should ask the user for consent before sharing analytics. You can use that second value to convey to the SDK whether the user has consented or not. If any of those values is false, the process is aborted, and nothing is sent to any server.

```java
PrivateAnalyticsEnabledProvider analyticsEnabledProvider = new PrivateAnalyticsEnabledProvider() {
  boolean isSupportedByApp() {
    // ...
  }
  boolean isEnabledForUser() {
    // ...
  }
};
```

### PrivateAnalyticsSubmitter

This is the final class needed, which orchestrates all the parts we've created so far. The following code shows how to invoke it:
```java
  PrivateAnalyticsSubmitter analyticsSubmitter = new PrivateAnalyticsSubmitter(dataPoints, remoteConfig, firestoreRepository, analyticsEnabledProvider);
  ListenableFuture submissionFuture = analyticsSubmitter.submitPackets();
  // You should then wait for the future to complete
```

This invokes the Prio algorithm on the data points you've submitted, tries to attach a device attestation to the result, and then uploads the result to the ingestion server through Firestore (if the provider said that analytics were enabled for this user).

### Optional: dependency injection

If instead of creating all those classes manually, you can use the Dependency Injection framework of your preference. `PrivateAnalyticsSubmitter`, `DefaultPrivateAnalyticsDeviceAttestation`, `DefaultPrivateAnalyticsRemoteConfig`, and `PrivateAnalyticsFirestoreRepository` are all annotated with `@javax.inject.Inject`, which is compatible with most DI frameworks.

### Optional: scheduling a recurrent submission with AndroidX WorkManager

Most users will regularly trigger an analytics submission (for example, on a daily basis). For that purpose, you can use a background service to invoke it on a periodic basis. We recommend the WorkManager API.

To help with that, the SDK contains a `SubmitPrivateAnalyticsWorker` that you should be able to use directly.
The basic usage to register this worker with WorkManager is:
```java
ListenableFuture<State.SUCCESS> scheduleFuture = SubmitPrivateAnalyticsWorker.schedule(workManager).getResult();
// You should then wait for the future to complete and check its value.
```

Note that since our worker needs dependencies, we need to specify to the `WorkManager` how those dependencies should be resolved.
The most common pattern is to specify a `WorkerFactory` when initializing the `WorkManager`.

```java
  WorkerFactory workerFactory = new WorkerFactory() {
    ListenableWorker createWorker(Context context, String workerClassName, WorkerParameters workerParameters) {
      if (SubmitPrivateAnalyticsWorker.WORKER_NAME.equals(workerClassName)) {
        // You can decide whether background tasks are enabled for your app or not:
        ListenableFuture<Boolean> isEnabledWithStartupTasks = Futures.immediateFuture(true);
        // You can specify a listener if you want a callback when the worker is started
        Optional<PrivateAnalyticsEventListener> listener = Optional.absent();
        return new SubmitPrivateAnalyticsWorker(context, workerParameters, privateAnalyticsSubmitter, isEnabledWithStartupTasks, listener);
      }
      // create other workers you may need for your app
    }
  };
  WorkManager workManager = WorkManager.initialize(context, configuration);
```
