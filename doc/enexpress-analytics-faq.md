# Analytics in Exposure Notifications Express: FAQ

### What analytics are available in Exposure Notifications Express?

Exposure Notifications Express (ENX) can provide public health authorities aggregate statistics about the system while protecting user privacy.

These aggregate statistics give health authorities the option to assess the effectiveness and improve their Exposure Notification deployment. These aggregate metrics will **only be available to public health authorities**.

The cryptographic protocol implemented as part of analytics in ENX is designed to prevent  participants in the system from learning information about specific individuals. The public health authority receives only aggregate statistics that cannot be linked to individuals.

### What analytics will be enabled?

Initially, ENX will support three types of metrics: the number of exposure notifications sent in a public health authority’s region, the number of user interactions (e.g. taps, dismissals) with exposure notifications, and histograms of the risk scores computed for users of the Exposure Notifications System in their region.

ENX may be updated to support additional metrics that are useful for health authorities. This FAQ will be updated if any new metrics become supported.

### Who are the parties involved as part of analytics in ENX?

- The architecture that will enable analytics in ENX in a privacy-preserving manner includes:
Two aggregation servers
  - A public health authority server (run in the US by the National Cancer Institute in the National Institutes of Health and MITRE Corporation) that receives the final aggregated data
  - A Facilitator server (run in the US by the Internet Security Research Group in partnership with Linux Foundation Public Health) that assists with the computation
- An ingestion server that runs device attestation and filters inputs so that only legitimate devices can contribute to the aggregate data.
  - Google will run an ingestion server.
  - The ingestion server only processes data encrypted with the PHA public key or the facilitator public key. The ingestion server cannot decrypt this data.
  - The ingestion server discards IP addresses and does not log or share them with any other parties.

Google’s role in enabling analytics is adding the capability to ENX, supporting third parties that will be deploying servers used for privacy preserving aggregation and deploying the ingestion server to check device attestation.

### How does analytics in ENX work?

Analytics in ENX uses a cryptographic multiparty computation protocol introduced in the [Prio](https://crypto.stanford.edu/prio/) system combined with an enhancement that provides central differential privacy for the output.

The user device adds a small amount of noise to the input (we use randomized response techniques -since in most cases the input is binary, this means that the input bit is flipped with some small probability). Then, the user device computes two cryptographic shares of the noised input it will contribute (each such share independently looks like a random number and does not reveal any information about the input, but when put together two shares can be used to reconstruct the input). The device also computes a distributed zero knowledge range proof, which proves that the input is within some prespecified public range without revealing any other information about the input. This proof is provided to prevent manipulation of the aggregate results.

The client encrypts one input share and one part of the range proof with the public key of the public health authority server and the other input share and the other part of the range proof with the public key of the Facilitator helper server. The user device sends the resulting ciphertexts to the ingestion server.

The ingestion server checks device attestation for the devices sending encrypted inputs. This prevents attacks from compromised devices sending illegitimate metrics contributions. For all contributions for which device attestation verifies, it splits the inputs for the public health authority and the Facilitator helper servers and sends them in batches to the two servers.

The public health authority and Facilitator servers decrypt the ciphertexts they receive from the ingestion server, run jointly the verification of the range proofs, and aggregate locally all inputs with valid proofs. The Facilitator helper server then sends its aggregate to the public health authority server, which adds it to its own aggregate to obtain the final aggregate metric.

### What information will Google, the PHA and the Facilitator helper server learn?

As described above, Google will not be able to read any of this data (even in aggregate form).

The Facilitator helper server will not learn any information about the inputs contributed by the clients except that they are valid inputs within the specified public ranges.

The public health authority will learn **only** the aggregates with noise for differential privacy and will be able to confirm valid inputs within the specified public ranges.

No information identifying a specific individual would be linkable by either party to the analytics collected.

### How can public health authorities enable analytics in ENX?

Public health authorities will need to opt into using analytics in ENX during the configuration process for ENX.

### Do individual users opt in to analytics in ENX?

Users will need to [opt in](https://support.google.com/android/answer/10162607) to analytics in ENX in the minted app. They control whether to contribute their data. They can also opt-out at any time they want to.
