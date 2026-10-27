# Mini Redis --- Java TCP Key-Value Store

A learning project that builds a small Redis-inspired, in-memory
key-value server using core Java networking and concurrency concepts.

> **Project status:** Learning project. The current implementation
> supports multiple TCP clients, `SET`, `GET`, basic command validation,
> and `QUIT`. It is not a drop-in replacement for Redis.

## Table of Contents

-   [Overview](#overview)
-   [Real Redis vs. Mini Redis](#real-redis-vs-mini-redis)
-   [Implemented Features](#implemented-features)
-   [Architecture](#architecture)
-   [Request Lifecycle](#request-lifecycle)
-   [Command Protocol](#command-protocol)
-   [Technology Stack](#technology-stack)
-   [Run Locally](#run-locally)
-   [Test with Multiple Clients](#test-with-multiple-clients)
-   [Project Structure](#project-structure)
-   [Current Limitations](#current-limitations)
-   [Roadmap](#roadmap)
-   [Learning Goals](#learning-goals)

## Overview

Mini Redis is a small server that accepts TCP client connections and
processes simple text commands. It stores string keys and values in
memory, allowing one client to write a value and another client to
retrieve it while the server is running.

The project focuses on learning:

-   TCP client/server communication with Java sockets.
-   Concurrent client handling with threads.
-   Shared state and thread-safe data structures.
-   Basic command parsing and request/response handling.
-   Graceful client disconnection and error handling.

## Real Redis vs. Mini Redis

The goal of Mini Redis is to recreate a small part of Redis's core idea: a
server that accepts commands over a network and stores key-value data in
memory. It is a learning implementation, not a complete Redis clone or a
drop-in replacement for the real Redis server.

| Area | Real Redis | Mini Redis |
|---|---|---|
| Purpose | Production-ready in-memory data store used for caching and other data-serving workloads | Learning project for understanding how a networked key-value server works |
| Server and networking | Redis server with its own client/server protocol | Java TCP server using `ServerSocket` and `Socket` |
| Data storage | Supports multiple data structures, including strings, hashes, lists, sets, and sorted sets | Stores string keys and string values in a shared `ConcurrentHashMap` |
| Commands | Supports a broad command set, including `SET`, `GET`, `DEL`, expiry commands, and many others | Implements `SET`, `GET`, and `QUIT`, plus basic command validation |
| Wire protocol | Uses the Redis Serialization Protocol (RESP) | Uses a simple line-based text protocol; it does not implement RESP |
| Concurrent clients | Designed to serve many clients with production-oriented networking and execution behavior | Starts a separate thread for each accepted client connection |
| Persistence | Provides persistence options, including snapshots and append-only files | Data is held in memory and is lost when the server process stops |
| Reliability and security | Offers a wider set of operational, security, and production features | No authentication, encryption, replication, or transaction support currently |
| Best use | Real applications that need Redis's supported capabilities | Learning Java networking, command processing, shared state, and concurrency |

### Example: how shared storage works

A client connected to Mini Redis can send:

```text
SET name Sriram
```

The server stores `name` → `Sriram` in its shared in-memory map and replies
`OK`. Another client can then send:

```text
GET name
```

and receive `Sriram`. This works because both clients connect to the same
server process and access the same map.

### What this project demonstrates

-   **Networking:** clients and server exchange commands over TCP sockets.
-   **Concurrency:** the server handles client connections on separate
    threads.
-   **Thread-safe shared state:** client handlers access the same
    `ConcurrentHashMap`.
-   **Command processing:** input is parsed, validated, executed, and
    translated into a response.

The project intentionally focuses on these fundamentals instead of
reimplementing every feature of Redis. Features such as RESP support,
persistence, key expiry, more commands, automated tests, and a bounded
thread pool are possible future improvements.

## Implemented Features

-   TCP server listening on port `6379`.
-   A separate thread for each accepted client connection.
-   Shared in-memory key-value storage using `ConcurrentHashMap`.
-   `SET key value` command to store a string value.
-   `GET key` command to retrieve a value.
-   `(nil)` response when a key does not exist.
-   Basic handling of empty input and invalid commands.
-   `QUIT` command to close one client's connection without stopping the
    server.

## Architecture

### High-level diagram

``` mermaid
flowchart TD
    C1[Client 1: netcat] -->|TCP connection| S[ServerSocket :6379]
    C2[Client 2: netcat] -->|TCP connection| S
    C3[Client N: netcat] -->|TCP connection| S

    S --> A[Main server thread]
    A -->|accept connection| T1[Client handler thread 1]
    A -->|accept connection| T2[Client handler thread 2]
    A -->|accept connection| TN[Client handler thread N]

    T1 --> P[Command parser]
    T2 --> P
    TN --> P

    P -->|SET / GET| M[(Shared ConcurrentHashMap)]
    M --> P

    P --> R[Text response]
    R --> T1
    R --> T2
    R --> TN
```

### Component responsibilities

  -------------------------------------------------------------------------
  Component                             Responsibility
  ------------------------------------- -----------------------------------
  `ServerSocket`                        Listens on port `6379` and accepts
                                        incoming TCP connections.

  Main server loop                      Calls `accept()` repeatedly so the
                                        server can accept additional
                                        clients.

  Client `Socket`                       Represents one client's TCP
                                        connection.

  Client handler thread                 Reads commands from one client and
                                        sends responses to that client.

  `BufferedReader`                      Reads incoming text one line at a
                                        time.

  `PrintWriter`                         Writes text responses to the
                                        client.

  Command parser                        Splits a line into a command and
                                        its arguments, then validates the
                                        command shape.

  `ConcurrentHashMap<String, String>`   Holds shared string keys and values
                                        and supports concurrent access by
                                        client threads.
  -------------------------------------------------------------------------

### Threading model

The main server thread accepts connections. Each accepted connection is
handed to a new worker thread, which runs `handleClient(...)`.

This means a client waiting for input does not prevent the main server
thread from accepting another client. The current learning
implementation creates one thread per client; it does not yet use a
bounded thread pool.

### Data model

The server stores data in a shared in-memory map:

-   Key type: `String`
-   Value type: `String`
-   Example: `name` → `Sriram`

The map is shared by all client handlers, so clients can read values
written by other clients. Data is not persisted to disk and is lost when
the server process stops.

## Request Lifecycle

1.  A client opens a TCP connection to `localhost:6379`.
2.  `ServerSocket.accept()` returns a `Socket` for that connection.
3.  The server starts a client handler thread.
4.  The handler reads a line of text from the client.
5.  The command parser identifies the command and arguments.
6.  For `SET`, the handler writes the key-value pair to the shared map.
7.  For `GET`, the handler reads the value from the shared map.
8.  The handler sends a text response to the same client.
9.  The handler continues until the client disconnects or sends `QUIT`.

## Command Protocol

Commands are plain text, one command per line.

  -----------------------------------------------------------------------------------------------------
  Command            Example             Expected response                            Meaning
  ------------------ ------------------- -------------------------------------------- -----------------
  `SET key value`    `SET name Sriram`   `OK`                                         Stores a string
                                                                                      value under a
                                                                                      key.

  `GET key`          `GET name`          `Sriram`                                     Returns the value
                                                                                      for an existing
                                                                                      key.

  `GET missingKey`   `GET missingKey`    `(nil)`                                      Indicates that
                                                                                      the key does not
                                                                                      exist.

  Empty line         Press Enter without No response                                  Empty input is
                     typing                                                           ignored.

  Unknown/invalid    `HELLO`             `ERR unknown command or invalid arguments`   Reports an
  command                                                                             unsupported
                                                                                      command or
                                                                                      invalid argument
                                                                                      count.

  `QUIT`             `QUIT`              `BYE`                                        Closes the
                                                                                      current client's
                                                                                      connection.
  -----------------------------------------------------------------------------------------------------

`SET` accepts a value containing spaces, for example
`SET greeting hello world`.

## Technology Stack

-   **Language:** Java
-   **Networking:** `java.net.ServerSocket`, `java.net.Socket`
-   **I/O:** `BufferedReader`, `InputStreamReader`, `PrintWriter`
-   **Concurrency:** Java threads
-   **Data structure:** `ConcurrentHashMap`
-   **Client testing:** `nc` (netcat) in macOS/Linux Terminal

No external libraries are required for the current implementation.

## Run Locally

### Prerequisites

-   A JDK installed.
-   Terminal access.
-   `nc` (netcat), which is commonly available on macOS.

Check Java is installed:

``` bash
# Print the installed Java runtime version.
java -version

# Print the Java compiler version.
javac -version
```

### Compile and start

Run these commands from the directory containing `Main.java`:

``` bash
# Compile the Java source file.
javac Main.java

# Start the server on port 6379.
java Main
```

Expected startup message:

``` text
Mini Redis listening on port 6379
```

Keep this terminal open while the server runs.

## Test with Multiple Clients

Open a second terminal and connect:

``` bash
# Connect a client to the local server on port 6379.
nc localhost 6379
```

Enter commands:

``` text
SET name Sriram
OK
GET name
Sriram
```

Open a third terminal and connect with the same `nc localhost 6379`
command. Run `GET name`; it should return `Sriram`, demonstrating that
the clients share the same in-memory store.

Try additional cases:

``` text
GET unknown
(nil)
HELLO
ERR unknown command or invalid arguments
QUIT
BYE
```

After `QUIT`, that client disconnects while the server continues running
for other clients.

To stop the server itself, focus the server terminal and press `Ctrl+C`.

## Project Structure

The repository can start with this simple structure:

``` text
mini-redis/
├── Main.java             # Server, client handler, command parsing, and in-memory store
├── README.md             # Project overview, architecture, setup, and usage
└── MainStep4.java.txt    # Optional learning backup; not compiled as Java source
```

If you keep historical backups in the repository, label them clearly as
learning snapshots. The active application source should remain
`Main.java`.

## Current Limitations

-   Data is stored only in memory; restarting the server clears it.
-   The protocol is a simple line-based text protocol, not the Redis
    Serialization Protocol (RESP).
-   The server creates one thread per client, without a bounded thread
    pool.
-   There is no authentication, authorization, or encryption.
-   There are no persistence, replication, transactions, or expiry/TTL
    features.
-   Automated tests and performance benchmarks have not yet been added.
-   Error handling is intentionally simple for learning purposes.

## Roadmap

-   [ ] Add `DEL` and `EXISTS` commands.
-   [ ] Add automated unit and integration tests.
-   [ ] Improve command parsing and protocol documentation.
-   [ ] Replace unbounded thread creation with a bounded executor/thread
    pool.
-   [ ] Add persistence using an append-only log or snapshots.
-   [ ] Add performance benchmarks for throughput and latency.
-   [ ] Consider key expiration (TTL) and additional commands.
-   [ ] Add CI to compile and run tests on every push.

## Learning Goals

This project is intended to demonstrate understanding of the
fundamentals behind a networked key-value service: accepting TCP
connections, reading and writing data over sockets, handling multiple
clients, sharing state safely, and designing a simple command protocol.

The goal is not to reproduce all Redis features, but to build the system
incrementally and understand the engineering trade-offs behind each
change.
