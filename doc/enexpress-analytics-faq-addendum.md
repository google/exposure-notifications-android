# Addendum to the Analytics in Exposure Notifications Express: FAQ

_This page includes additional information on the analytics in Exposure Notifications Express (ENX) outside the United States, which was previously launched in December 2020. See the [FAQ](enexpress-analytics-faq.md) for general information regarding analytics in ENX and for information on the server configuration for these analytics in the U.S._

The architecture that will enable analytics in ENX in a privacy-preserving manner outside of the United States includes:

- A public health authority (PHA) server that can access the final aggregated data.
    - The PHA server is split into two parts.
        - An aggregator server that will not know the output aggregate metric or the inputs contributed by the clients (except that they are valid inputs within the specified public ranges)
        - A reconstructor server that combines the output of the aggregator and the facilitator servers to obtain the aggregate differentially private metric.
    - Outside the US, the aggregator server is operated by Google and the reconstructor server is operated by MITRE.
- A facilitator server that assists with the computation. This will continue to be run by the Internet Security Research Group.
- An ingestion server. Google will continue to run an ingestion server, as described in the [FAQ](enexpress-analytics-faq.md).
