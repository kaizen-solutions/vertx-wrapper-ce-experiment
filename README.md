# Vertx PG Client Cats Effect Testbed

This is a testbed for how to integrate the Vertx Postgres SQL Client with Cats Effect with a focus on providing algebraic primitives
and a semi-high level API.

## Goals
* SQL Interpolation like Doobie
* Encoders and Decoders for common types with automatic derivation
* High-level API for common use cases
* Low-level APIs for complex use cases
* Streaming
* Batching