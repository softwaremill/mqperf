![Intro](evaluating-message-queues.png)

# Introduction

Message queues are central to many distributed systems and often provide a backbone for asynchronous processing and communication between (micro)services. They are useful in a number of situations. Any time we want to **execute a task** asynchronously, we put the task on a queue; some executor (could be another thread, process or machine) eventually runs it. Or, one component might produce a **stream of events**, which are stored by the message queue. Other, decoupled components, consume the events asynchronously, either on-line or after some period of time.

Various message queue implementations can give various guarantees on message persistence and delivery. For some use-cases, it is enough to have an in-memory, volatile message queue. For others, we want to be sure that once the message send completes, the message is **persistently enqueued** and will be eventually delivered, despite node or system crashes.

The mqperf tests inspect systems on the 'safe' side of this spectrum, which try to make sure that messages are not lost by:

* persisting messages to disk
* replicating messages across the network

We will examine the characteristics of a number of message queueing and data streaming systems, comparing their features, replication schemes, supported protocols, operational complexity and performance. All of these factors might impact which system is best suited for a given task. In some cases, you might need top-performance, which might come with tradeoffs in terms of other features. In others, performance isn’t the main factor, but instead compatibility with existing protocols, message routing capabilities or deployment overhead play the central role.

When talking about performance, we’ll take into account both **how many messages per second** a given queueing system can process, but also at the **processing latency**, which is an important factor in systems which should react to new data in near real-time (as is often the case with various event streams). Another important aspect is message **send latency**, that is how long it takes for a client to be sure a message is persisted in the queueing system. This may directly impact e.g. the latency of http endpoints and end-user experience.

## Version history

<table>
  <tbody>
    <tr>
      <td>26 Jul 2021</td>
      <td>
          Refresh of the 2020 edition. Tests of RedPanda and Redis Streams. Added encryption and compression info to the summary.
          Co-authored with [Bartłomiej Turos](https://github.com/bturos) and [Robert Dziewoński](https://github.com/robert-dziewonski). 
      </td>
    </tr>
    <tr>
      <td>8 Dec 2020</td>
      <td>
          2020 edition: extended feature comparison, updated benchmarks, new queues (Pulsar, PostgreSQL, Nats Streaming); dropping ActiveMQ 5 in favor of ActiveMQ Artemis. Co-authored with [Kasper Kondzielski](https://twitter.com/kkondzielski). 
      </td>
    </tr>
    <tr>
      <td>1 August 2017</td>
      <td>Updated the results for Artemis, using memory-mapped journal type and improved JMS test client</td>
    </tr>
    <tr>
      <td>18 July 2017</td>
      <td><a href="/mqperf-2017">2017 edition</a>: updating with new versions; adding latency measurements; adding Artemis and EventStore</td>
    </tr>
    <tr>
      <td>4 May 2015</td>
      <td><a href="/mqperf-2015">2015 edition</a>: updated with new versions, added ActiveMQ; new site</td>
    </tr>
    <tr>
      <td>1 July 2014</td>
      <td>original at <a href="http://www.warski.org/blog/2014/07/evaluating-persistent-replicated-message-queues/">Adam Warski's blog</a></td>
    </tr>
  </tbody>
</table>

## Tested queues

There is a number of open-source messaging projects available, but only some support both persistence and replication. We'll evaluate the performance and characteristics of 12 message queues, in no particular order:

* [Amazon SQS](http://aws.amazon.com/sqs/)
* [MongoDB](http://www.mongodb.com/)
* [PostgreSQL](https://www.postgresql.org)
* [RabbitMq](http://www.rabbitmq.com/)
* [Kafka](https://kafka.apache.org/)
* [Pulsar](https://pulsar.apache.org)
* [ActiveMQ Artemis](http://activemq.apache.org/components/artemis/)
* [RocketMQ](https://rocketmq.apache.org)
* [NATS Streaming](https://docs.nats.io/developing-with-nats-streaming/streaming)
* [EventStore](https://www.eventstore.com)
* [RedPand](https://vectorized.io)
* [Redis Streams](https://redis.io/topics/streams-intro)

You might rightfully notice that not all of these are message queueing systems. Both MongoDB and PostgreSQL (and to some degree, EventStore) are general-purpose databases. However, using some of their mechanisms it’s possible to implement a message queue on top of them. If such a simple queue meets the requirements and the database system is already deployed for other purposes, it might be reasonable to reuse it and reduce operational costs.

Except for SQS, all of the systems are open-source, and can be self-hosted on your own servers or using any of the cloud providers, both directly or through Kubernetes. Some systems are also available as hosted, as-a-service offerings.

# Queue characteristics

We’ll be testing and examining the performance of a single, specific queue usage scenario in detail, as well as discussing other possible queue-specific use-cases more generally.

In our scenario, as mentioned in the introduction, we’ll put a focus on safety. The scenario tries to reflect a reasonable default that you might start with when developing applications leveraging a message queue, however by definition we’ll cover only a fraction of possible use-cases.

There are three basic operations on a queue which we'll be using:

* **sending** a message to the queue
* **receiving** a message from the queue
* **acknowledging** that a message has been processed

On the **sender** side, we want to have a guarantee that if a message send call completes successfully, the message will be eventually processed. Of course, we will never get a 100% "guarantee", so we have to accept some scenarios in which messages will be lost, such as a catastrophic failure destroying all of our geographically distributed servers. Still, we want to minimise message loss. That's why:

* messages should survive a restart of the server, that is messages should be *persisted* to a durable store (hard disk). However, we accept losing messages due to unflushed disk buffers (we do not require `fsync`s to be done for each message).
* messages should survive a permanent failure of a server, that is messages should be *replicated* to other servers. We'll be mostly interested in **synchronous replication**, that is when the send call can only complete after the data is replicated. Note that this additionally protects from message loss due to hard disk buffers not being flushed. Some systems also offer **asynchronous replication**, where messages are accepted before being replicated, and thus there's more potential for message loss. We'll make it clear later which systems offer what kind of replication.

On the **receiver** side, we want to be able to receive a message and then **acknowledge** that the message was processed successfully. Note that receiving alone should not remove the message from the queue, as the receiver may crash at any time (including right after receiving, before processing). But that could also lead to messages being processed twice (e.g. if the receiver crashes after processing, before acknowledging); hence our queue should support **at-least-once delivery**.

With at-least-once delivery, message processing should be _idempotent_, that is processing a message twice shouldn't cause any problems. Once we assume that characteristic, a lot of things are simplified; message acknowledgments can be done asynchronously, as no harm is done if an ack is lost and the message re-delivered. We also don't have to worry about distributed transactions. In fact, no system can provide exactly-once delivery when integrating with external systems (and if it claims otherwise: read the fine print); it's always a choice between at-most-once and at-least-once.

By requiring idempotent processing, the life of the message broker is easier, however, the cost is shifted to writing application code appropriately.

# Performance testing methodology

We'll be performing three measurements during the tests:

* **throughput in messages/second**: how fast on average the queue is, that is how many messages per second can be sent, and how many messages per second can be received & acknowledged
* **95th percentile of processing latency** (over a 1-minute window): how much time (in milliseconds) passes between a message send and a message receive. That is, how fast the broker passes the message from the sender to the receiver
* **95th percentile of send latency** (over a 1-minute window): how long it takes for a message send to complete. That's when we are sure that the message is safely persisted in the cluster, and can e.g. respond to our client's http request "message received".

When setting up the queues, our goal is to have 3 identical, replicated nodes running the message queue server, with automatic fail-over. That’s not always possible with every queue implementation, hence we’ll make it explicit what’s the replication setup in each case.

The sources for the tests as well as the Ansible scripts used to setup the queues are [available on GitHub](https://github.com/softwaremill/mqperf).

Each test run is parametrised by the type of the message queue tested, optional message queue parameters, number of client nodes, number of threads on each client node and message count. A client node is either sending or receiving messages; in the tests we used from 1 to 20 client nodes of each type, each running from 1 to 100 threads. By default there are twice as many receiver nodes as sender nodes, but that’s not a strict rule and we’re modifying these proportions basing on what’s working best for a given queue implementation.

Each [Sender](https://github.com/softwaremill/mqperf/blob/master/src/main/scala/com/softwaremill/mqperf/Sender.scala) thread tries to send the given number of messages as fast as possible, in batches of random size between 1 and 10 messages. The messages are picked from a pool of messages, randomly generated on startup.

The [Receiver](https://github.com/softwaremill/mqperf/blob/master/src/main/scala/com/softwaremill/mqperf/Receiver.scala) tries to receive messages (also in batches of up to 10 messages), and after receiving them, acknowledges their delivery (which should cause the message to be removed from the queue). The test ends when no messages are received for a minute.

![MQ test setup](mqtestsetup.83a9fe43.png)

The queues have to implement the [Mq](https://github.com/softwaremill/mqperf/blob/master/src/main/scala/com/softwaremill/mqperf/mq/Mq.scala) interface. The methods should have the following characteristics:

* `send` should be synchronous, that is when it completes, we want to be sure (what "sure" means exactly may vary) that the messages are sent
* `receive` should receive messages from the queue and block them from being received by other clients; if the node crashes, the messages should be returned to the queue and re-delivered, either immediately or after a time-out
* `ack` should acknowledge delivery and processing of the messages. Acknowledgments can be asynchronous, that is we don't have to be sure that the messages really got deleted

## Server setup

Both the clients, and the messaging servers used [r5.2xlarge memory-optimized EC2 instances](http://aws.amazon.com/ec2/instance-types/); each such instance has 8 virtual CPUs, 64GiB of RAM and SSD storage (in some cases additional `gp2` disks where used).

All instances were started in a _single_ availability zone (eu-west-1). While for production deployments it is certainly better to have the replicas distributed across different locations (in EC2 terminology - different availability zones), but as the aim of the test was to measure performance, a single availability zone was used to minimise the effects of network latency as much as possible.

The servers were provisioned automatically using [Ansible](https://www.ansible.com). All of the playbooks are available in the [github repository](https://github.com/softwaremill/mqperf/tree/master/ansible), hence the tests should be reproducible.

Test results were aggregated using [Prometheus](https://prometheus.io) and visualized using [Grafana](https://grafana.com). We'll see some dashboard snapshots with specific results later.

While the above might not guarantee the best possible performance for each queue (we might have used `r5.24xlarge` for example), the goal was to get some common ground for comparison between the various systems. Hence, the results should be treated only comparatively, and the tests should always be repeated in the target environment before making any decisions.

# MongoDB

<table>
  <tbody>
    <tr>
      <td><em>Version</em></td>
      <td>server 4.2, java driver 3.12.5</td>
    </tr>
    <tr>
      <td><em>Replication</em></td>
      <td>configurable, asynchronous &amp; synchronous</td>
    </tr>
    <tr>
      <td><em>Replication type</em></td>
      <td>active-passive</td>
    </tr>
  </tbody>
</table>

Mongo has two main features which make it possible to easily implement a durable, replicated message queue on top of it: very simple replication setup (we'll be using a 3-node replica set), and various document-level atomic operations, like `find-and-modify`. The implementation is just a handful of lines of code; take a look at [MongoMq](https://github.com/softwaremill/mqperf/blob/master/src/main/scala/com/softwaremill/mqperf/mq/MongoMq.scala).

Replication in Mongo follows a leader-follower setup, that is there’s a single node handling writes, which get replicated to follower nodes. As an optimization, reads can be offloaded to followers, but we won’t be using this feature here. Horizontal scaling is possible by sharding and using multiple replica sets, but as far as message queueing is concerned this would make the queue implementation significantly more complex. Hence this queue implementation is bound by the capacity of the leader node.

Network partitions (split-brain scenarios), which are one of the most dangerous fault types in replicated databases, [are handled](https://docs.mongodb.com/manual/core/replica-set-elections/#network-partition) by making sure that only the partition with the majority of nodes is operational.

We are able to control the guarantees which `send` gives us by using an appropriate write concern when writing new messages:

* `WriteConcern.W1` ensures that once a send completes, the messages have been written to disk (but the buffers may not be yet flushed, so it's not a 100% guarantee) on a single node; this corresponds to asynchronous replication
* `WriteConcern.W2` ensures that a message is written to at least 2 nodes (as we have 3 nodes in total, that's a majority) in the cluster; this corresponds to synchronous replication

The main downside of the Mongo-based queue is that:

* messages can't be received in bulk – the `find-and-modify` operation only works on a single document at a time
* when there's a lot of connections trying to receive messages, the collection will encounter a lot of contention, and all operations are serialised.

And this shows in the results: sends are much faster than receives. But the performance isn’t bad despite this.

A single-thread, single-node, synchronous replication setup achieves **958 msgs/s** sent and received. The maximum send throughput with multiple thread/nodes that we were able to achieve is about 11 286 msgs/s (25 threads, 2 nodes), while the maximum receive rate is **7 612 msgs/s** (25 threads, 2 nodes). An interesting thing to note is that the receive throughput quickly achieves its maximum value, and adding more threads (clients) only decreases performance. The more concurrency, the lower overall throughput.

![MongoDB](mongodb.png)

With asynchronous replication, the results are of course better: up to 38 120 msg/s sent and 8 130 msgs/s received. As you can see, here the difference between send and receive performance is even bigger.

What about latencies? In both synchronous and asynchronous tests, the send latency is about **48 ms**, and this doesn't deteriorate when adding more concurrent clients.

As for the processing latency, measurements only make sense when the receive rate is the same as the sending rate. When the clients aren't able to receive messages as fast as they are sent, the processing time goes arbitrarily up.

With 2 nodes running 5 threads each, Mongo achieved a throughput of **3 913 msgs/s** with a processing latency of **48 ms**. Anything above that caused receives to fall back behind sends. Here's the dashboard for that test:

<a href="https://snapshot.raintank.io/dashboard/snapshot/29VhWbdaTCaL5j3JqC5cz5yexuQ9WWm1" target="_blank">
![MongoDB grafana](mongodb%20grafana.png)
</a>

Performance results in detail when using synchronous replication are as follows:

<table>
  <thead>
    <tr>
      <th>Threads</th>
      <th>Sender nodes</th>
      <th>Receiver nodes</th>
      <th>Send msgs/s</th>
      <th>Receive msgs/s</th>
      <th>Processing latency</th>
      <th>Send latency</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>1</td>
      <td>1</td>
      <td>2</td>
      <td>958,00</td>
      <td>958,00</td>
      <td>48,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>1</td>
      <td>2</td>
      <td>3 913,00</td>
      <td>3 913,00</td>
      <td>48,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>1</td>
      <td>2</td>
      <td>10 090,00</td>
      <td>7 612,00</td>
      <td>60000,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>1</td>
      <td>2</td>
      <td>4</td>
      <td>1 607,00</td>
      <td>1 607,00</td>
      <td>48,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>2</td>
      <td>4</td>
      <td>5 532,00</td>
      <td>5 440,00</td>
      <td>60000,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>2</td>
      <td>4</td>
      <td>11 286,00</td>
      <td>4 489,00</td>
      <td>60000,00</td>
      <td>53,00</td>
    </tr>
  </tbody>
</table>

Overall, if you are already using Mongo in your application, you have small traffic and don’t need to use any of the more advanced messaging protocols or features, a queue implementation on top of Mongo might work just fine.

# PostgreSQL

<table>
  <tbody>
    <tr>
      <td><em>Version</em></td>
      <td>server 12.4, java driver 42.2.12</td>
    </tr>
    <tr>
      <td><em>Replication</em></td>
      <td>configurable, asynchronous &amp; synchronous</td>
    </tr>
    <tr>
      <td><em>Replication type</em></td>
      <td>active-passive</td>
    </tr>
  </tbody>
</table>

When implementing a queue on top of PostgreSQL, we are using a single `jobs` table:

```sql
CREATE TABLE IF NOT EXISTS jobs(
  id UUID PRIMARY KEY, 
  content TEXT NOT NULL, 
  next_delivery TIMESTAMPTZ NOT NULL)
```

Sending a message amounts to inserting data to the table. Receiving a message bumps the next delivery timestamp, making the message invisible for other receivers for some period of time (during which we assume that the message should be processed, or is otherwise redelivered). This is similar to how SQS works, which is discussed next. Acknowledging a message amounts to deleting the message from the database.

When receiving messages, we issue two queries (in a single transaction!). The first looks up the messages to receive, and puts a write lock on them. The second updates the next delivery timestamp:

```sql
SELECT id, content FROM jobs WHERE next_delivery <= $now FOR UPDATE SKIP LOCKED LIMIT n
UPDATE jobs SET next_delivery = $nextDelivery WHERE id IN (...)
```

Thanks to transactionality, we make sure that a single message is received by a single receiver at any time. Using write locks, `FOR UPDATE` and `SKIP LOCKED` help improve the performance by allowing multiple clients to receive messages concurrently, trying to minimise contention.

As with other messaging systems, we replicate data. PostgreSQL uses leader-follower replication, by setting the following configuration options, as described [in a blog post by Kasper](https://blog.softwaremill.com/quorum-replication-on-postgresql-7dbf2f340cd):
* `synchronous_standby_names` is set to `ANY 1 (slave1, slave2)`
* `synchronous_commit` is set to `remote_write`

It’s also possible to configure asynchronous replication, as well as require an fsync after each write. However, an important limitation of PostgreSQL is that by default, there’s **no automatic failover**. In case the leader fails, one of the followers must be promoted by hand (which, in a way, solves the split brain problem). There are however both open-source and commercial solutions, which provide modules for automatic failover.

In terms of performance, a baseline single-thread setup achieves around **3 800 msgs/s** sent and received. Such a queue can handle at most **23 000 msgs/s** sent and received using 5 threads on 2 sending and 4 receiving nodes:

<a href="https://snapshot.raintank.io/dashboard/snapshot/zGh0t1KlScAvA2zScYZxw2KbW8FQr72W" target="_blank">
    ![PostgreSQL grafana](postgresql%20grafana.png)
</a>

Increasing concurrency above that causes receive performance to degrade:

![PostgreSQL](postgresql.png)

Send latency is usually at 48ms. However, total processing latency is quite poor. As with Mongo, taking into account only the tests where the send throughput was on par with receive throughput, processing latency varied from **1172 ms** to **17 975 ms**. Here are the results in full:

<table>
  <thead>
    <tr>
      <th>Threads</th>
      <th>Sender nodes</th>
      <th>Receiver nodes</th>
      <th>Send msgs/s</th>
      <th>Receive msgs/s</th>
      <th>Processing latency</th>
      <th>Send latency</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>1</td>
      <td>1</td>
      <td>2</td>
      <td>4 034,00</td>
      <td>3 741,00</td>
      <td>60000,00</td>
      <td>47,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>1</td>
      <td>2</td>
      <td>15 073,00</td>
      <td>15 263,00</td>
      <td>5738,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>1</td>
      <td>2</td>
      <td>2 267,00</td>
      <td>2 317,00</td>
      <td>17957,00</td>
      <td>846,00</td>
    </tr>
    <tr>
      <td>1</td>
      <td>2</td>
      <td>4</td>
      <td>6 648,00</td>
      <td>7 530,00</td>
      <td>60000,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>2</td>
      <td>4</td>
      <td>23 220,00</td>
      <td>23 070,00</td>
      <td>1172,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>2</td>
      <td>4</td>
      <td>28 867,00</td>
      <td>20 492,00</td>
      <td>60000,00</td>
      <td>49,00</td>
    </tr>
    <tr>
      <td>1</td>
      <td>4</td>
      <td>8</td>
      <td>11 621,00</td>
      <td>11 624,00</td>
      <td>1372,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>4</td>
      <td>8</td>
      <td>23 300,00</td>
      <td>23 216,00</td>
      <td>4730,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>8</td>
      <td>4</td>
      <td>8</td>
      <td>22 300,00</td>
      <td>20 869,00</td>
      <td>60000,00</td>
      <td>49,00</td>
    </tr>
  </tbody>
</table>

Same as with MongoDB, if you require a very basic message queue implementation without a lot of traffic, and already have a replicated PostgreSQL instance in your deployment, such an implementation might be a good choice. Things to look out for in this case are long processing latencies and manual failover, unless third-party extensions are used.

# Event Store

<table>
  <tbody>
    <tr>
      <td><em>Version</em></td>
      <td>20.6.1, JVM client 7.3.0</td>
    </tr>
    <tr>
      <td><em>Replication</em></td>
      <td>synchronous</td>
    </tr>
    <tr>
      <td><em>Replication type</em></td>
      <td>active-passive</td>
    </tr>
  </tbody>
</table>

[EventStore](https://www.eventstore.com) is first and foremost a database for **event sourcing** and complex event processing. However, it also supports the competing consumers pattern, or as we know it: message queueing. How does it stack up comparing to other message brokers?

EventStore offers a lot in terms of creating event streams, subscribing to them and transforming through projections. In the tests we'll only be writing events to a stream (each message will become an event), and create persistent subscriptions (that is, subscriptions where the consumption state is stored on the server) to read events on the clients. You can read more about event sourcing, competing consumers and subscription types [in the docs](https://www.eventstore.com/blog/what-is-event-sourcing).

To safely replicate data, EventStore uses **quorum-based replication**, using a gossip protocol to disseminate knowledge about the cluster state. A majority of nodes has to confirm every write for it to be considered successful. That’s also how resilience against split brain is implemented.

As all of the tests are JVM-based, we'll be using the [JVM client](https://github.com/EventStore/EventStore.JVM), which is built on top of [Akka](http://akka.io) and hence fully non-blocking. However, the test framework is synchronous - because of that, the [EventStoreMq](https://github.com/softwaremill/mqperf/blob/master/src/main/scala/com/softwaremill/mqperf/mq/EventStoreMq.scala) implementation hides the asynchronous nature behind synchronous sender and receiver interfaces. Even though the tests will be using multiple threads, all of them will be using only one connection to EventStore per node.

Comparing to the default configuration, the client has a few modified options:

* `readBatchSize`, `historyBufferSize` and `maxCheckPointSize` are all bumped to `1000` to allow more messages to be pre-fetched
* the in-flight messages buffer size is increased from the default `10` to a `1000`. As this is by default not configurable in the JVM client, we had to copy some code from the driver and adjust the properties (see the `MyPersistentSubscriptionActor` class)

How does EventStore perform? A baseline setup achieves **793 msgs/s**, and when using 3 sender with 25 threads, and 4 receiver nodes, in the tests we have achieved a throughput of **33 427 msgs/s** with the 95th percentile of processing latency being at most **251 ms** and the send latency **49 ms**. Receive rates are stable, as are the latencies:

<a href="https://snapshot.raintank.io/dashboard/snapshot/hFfYHxCQZ3gwxw3SHeJZZLicAbucoVho?orgId=2" target="_blank">
![EventStore grafna](eventstore%20grafana.png)
</a>

Here's a summary of the EventStore tests that we've run:

<table>
  <thead>
    <tr>
      <th>Threads</th>
      <th>Sender nodes</th>
      <th>Receiver nodes</th>
      <th>Send msgs/s</th>
      <th>Receive msgs/s</th>
      <th>Processing latency</th>
      <th>Send latency</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>1</td>
      <td>1</td>
      <td>2</td>
      <td>793,00</td>
      <td>792,00</td>
      <td>116,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>1</td>
      <td>2</td>
      <td>16 243,00</td>
      <td>16 241,00</td>
      <td>123,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>2</td>
      <td>4</td>
      <td>29 076,00</td>
      <td>29 060,00</td>
      <td>128,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>3</td>
      <td>4</td>
      <td>33 427,00</td>
      <td>33 429,00</td>
      <td>251,00</td>
      <td>49,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>4</td>
      <td>4</td>
      <td>29 321,00</td>
      <td>27 792,00</td>
      <td>60000,00</td>
      <td>48,00</td>
    </tr>
  </tbody>
</table>

![EventStore](eventstore.png)

EventStore also provides a handy web console. Comparing to the other general-purpose databases (PostgreSQL and MongoDB), EventStore offers the best performance, but it’s also the most specialised, oriented towards working with event streams in the first place.

# SQS

<table>
  <tbody>
    <tr>
      <td><em>Version</em></td>
      <td>Amazon Java SDK 1.11.797</td>
    </tr>
    <tr>
      <td><em>Replication</em></td>
      <td>?</td>
    </tr>
    <tr>
      <td><em>Replication type</em></td>
      <td>?</td>
    </tr>
  </tbody>
</table>

SQS, [Simple Message Queue](http://aws.amazon.com/sqs), is a message-queue-as-a-service offering from Amazon Web Services. It supports only a handful of messaging operations, far from the complexity of e.g. [AMQP](http://www.amqp.org/), but thanks to the easy to understand interfaces, and the as-a-service nature, it is very useful in a number of situations.

The primary interface to access SQS and send messages is using an SQS-specific HTTP API. SQS provides at-least-once delivery. It also guarantees that if a send completes, the message is replicated to multiple nodes; quoting from [the website](http://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/Welcome.html):

> "Amazon SQS runs within Amazon's high-availability data centers, so queues will be available whenever applications need them. To prevent messages from being lost or becoming unavailable, all messages are stored redundantly across multiple servers and data centers."

When receiving a message, it is blocked from other receivers for a period of time called the visibility timeout. If the message isn’t deleted (acknowledged) before that time passes, it will be re-delivered, as the system assumes that previous processing has failed. SQS also offers features such as deduplication ids and FIFO queues. For testing, the [ElasticMQ](https://github.com/softwaremill/elasticmq) projects offers an in-memory implementation.

We don't really know how SQS is implemented, but it most probably spreads the load across many servers, so including it here is a bit of an unfair competition: the other systems use a single fixed 3-node replicated cluster, while SQS can employ multiple replicated clusters and route/balance the messages between them. Still, it might be interesting to compare to self-hosted solutions.

A baseline single thread setup achieves **592 msgs/s** sent and the same number of msgs received, with a processing latency of **113 ms** and send latency of **49 ms**.

These results are not impressive, but SQS scales nicely both when increasing the number of threads, and the number of nodes. On a single node, with 50 threads, we can send up to **22 687 msgs/s**, and receive on two nodes up to **14 423 msgs/s**.

With 12 sender and 24 receiver nodes, these numbers go up to **130 956 msgs/s** sent, and **130 976 msgs/s** received! However, at these message rates, the service costs might outweigh the costs of setting up a self-hosted message broker.

![SQS](sqs.png)

As for latencies, SQS can be quite unpredictable than other queues which we’ll cover later. We've observed processing latency from 94 ms up to **1 960 ms**. Send latencies are more constrained and are usually around **50 ms**.

Here's the dashboard for the test using 4 nodes, each running 5 threads.

<a href="https://snapshot.raintank.io/dashboard/snapshot/n9Ke2g8kZu3iTDav8oFwAf4307W0ADJQ" target="_blank">
![SQS grafana](sqs%20grafana.png)
</a>

And full test results:

<table>
  <thead>
    <tr>
      <th>Threads</th>
      <th>Sender nodes</th>
      <th>Receiver nodes</th>
      <th>Send msgs/s</th>
      <th>Receive msgs/s</th>
      <th>Processing latency</th>
      <th>Send latency</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>1</td>
      <td>1</td>
      <td>2</td>
      <td>528,00</td>
      <td>529,00</td>
      <td>113,00</td>
      <td>49,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>1</td>
      <td>2</td>
      <td>2 680,00</td>
      <td>2 682,00</td>
      <td>277,00</td>
      <td>49,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>1</td>
      <td>2</td>
      <td>13 345,00</td>
      <td>13 370,00</td>
      <td>1 960,00</td>
      <td>49,00</td>
    </tr>
    <tr>
      <td>50</td>
      <td>1</td>
      <td>2</td>
      <td>22 687,00</td>
      <td>14 423,00</td>
      <td>60 000,00</td>
      <td>49,00</td>
    </tr>
    <tr>
      <td>1</td>
      <td>2</td>
      <td>4</td>
      <td>1 068,00</td>
      <td>1 068,00</td>
      <td>97,00</td>
      <td>49,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>2</td>
      <td>4</td>
      <td>5 383,00</td>
      <td>5 377,00</td>
      <td>106,00</td>
      <td>49,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>2</td>
      <td>4</td>
      <td>26 576,00</td>
      <td>26 557,00</td>
      <td>302,00</td>
      <td>49,00</td>
    </tr>
    <tr>
      <td>1</td>
      <td>4</td>
      <td>8</td>
      <td>2 244,00</td>
      <td>2 245,00</td>
      <td>97,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>4</td>
      <td>8</td>
      <td>11 353,00</td>
      <td>11 356,00</td>
      <td>90,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>4</td>
      <td>8</td>
      <td>44 586,00</td>
      <td>44 590,00</td>
      <td>256,00</td>
      <td>50,00</td>
    </tr>
    <tr>
      <td>1</td>
      <td>8</td>
      <td>16</td>
      <td>3 651,00</td>
      <td>3 651,00</td>
      <td>97,00</td>
      <td>50,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>8</td>
      <td>16</td>
      <td>17 575,00</td>
      <td>17 577,00</td>
      <td>94,00</td>
      <td>50,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>8</td>
      <td>16</td>
      <td>84 512,00</td>
      <td>84 512,00</td>
      <td>237,00</td>
      <td>50,00</td>
    </tr>
    <tr>
      <td>1</td>
      <td>12</td>
      <td>24</td>
      <td>5 168,00</td>
      <td>5 168,00</td>
      <td>96,00</td>
      <td>50,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>12</td>
      <td>24</td>
      <td>25 735,00</td>
      <td>25 738,00</td>
      <td>94,00</td>
      <td>50,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>12</td>
      <td>24</td>
      <td>130 956,00</td>
      <td>130 976,00</td>
      <td>213,00</td>
      <td>50,00</td>
    </tr>
  </tbody>
</table>

# RabbitMQ

<table>
  <tbody>
    <tr>
      <td><em>Version</em></td>
      <td>3.8.5-1, java amqp client 5.9.0</td>
    </tr>
    <tr>
      <td><em>Replication</em></td>
      <td>synchronous</td>
    </tr>
    <tr>
      <td><em>Replication type</em></td>
      <td>active-passive</td>
    </tr>
  </tbody>
</table>

RabbitMQ is one of the leading open-source messaging systems. It is written in Erlang, implements [AMQP](http://www.amqp.org/) and is a very popular choice when messaging is involved; using RabbitMQ it is possible to define complex message delivery topologies. It supports both message persistence and replication.

We'll be testing a 3-node Rabbit cluster, using [quorum queues](https://www.rabbitmq.com/quorum-queues.html), which are a relatively new addition to what RabbitMQ offers. Quorum queues are based on the Raft consensus algorithm; a leader is automatically elected in case of node failure, as long as a majority of nodes are available. That way, data is safe also in case of network partitions.

To be sure that sends complete successfully, we'll be using [publisher confirms](http://www.rabbitmq.com/confirms.html), a Rabbit extension to AMQP, instead of transactions:

> "Using standard AMQP 0-9-1, the only way to guarantee that a message isn't lost is by using transactions -- make the channel transactional, publish the message, commit. In this case, transactions are unnecessarily heavyweight and decrease throughput by a factor of 250. To remedy this, a confirmation mechanism was introduced."

A message is confirmed after it has been replicated to a majority of nodes (this is where Raft is used). Moreover, messages have to be written to disk and fsynced.

Such strong guarantees are probably one of the reasons for mediocre performance. A basic single-thread setup achieves around **2 000 msgs/s** sent&received, with a processing latency of **18 000 ms** and send latency of **48 ms**. The queue can be scaled up to **19 000 msgs/s** using 25 threads, 4 sender nodes and 8 receiver nodes:

<table>
  <thead>
    <tr>
      <th>Threads</th>
      <th>Sender nodes</th>
      <th>Receiver nodes</th>
      <th>Send msgs/s</th>
      <th>Receive msgs/s</th>
      <th>Processing latency</th>
      <th>Send latency</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>1</td>
      <td>1</td>
      <td>2</td>
      <td>2 064,00</td>
      <td>1 991,00</td>
      <td>18 980,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>1</td>
      <td>2</td>
      <td>8 146,00</td>
      <td>8 140,00</td>
      <td>98,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>1</td>
      <td>2</td>
      <td>17 334,00</td>
      <td>17 321,00</td>
      <td>122,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>1</td>
      <td>2</td>
      <td>4</td>
      <td>3 994,00</td>
      <td>3 983,00</td>
      <td>1 452,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>2</td>
      <td>4</td>
      <td>12 714,00</td>
      <td>12 730,00</td>
      <td>99,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>2</td>
      <td>4</td>
      <td>19 120,00</td>
      <td>19 126,00</td>
      <td>142,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>1</td>
      <td>4</td>
      <td>8</td>
      <td>6 939,00</td>
      <td>6 941,00</td>
      <td>98,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>4</td>
      <td>8</td>
      <td>16 687,00</td>
      <td>16 685,00</td>
      <td>116,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>4</td>
      <td>8</td>
      <td>19 035,00</td>
      <td>19 034,00</td>
      <td>190,00</td>
      <td>71,00</td>
    </tr>
  </tbody>
</table>

![RabbitMQ](rabbitmq.png)

Let’s take a closer look at the test which achieves highest performance:

<a href="https://snapshot.raintank.io/dashboard/snapshot/QotKxMwyYw7F1jSIngM8P4wp1Ap6GE8d" target="_blank">
    ![RabbitMQ grafana](rabbitmq%20grafana.png)
</a>

As you can see, the receive rate, send and processing latencies are quite stable - which is also an important characteristic to examine under load. The processing latency is around **190 ms**, while the send latency in this case is **71 ms**.

Note that messages are always sent and received at the same rate, which would indicate that message sending is the limiting factor when it comes to throughput. Rabbit's performance is a consequence of some of the features it offers, for a comparison with Kafka see for example [this Quora question](https://www.quora.com/Why-does-Kafka-scale-better-than-other-messaging-systems-like-RabbitMQ).

The [RabbitMq](https://github.com/softwaremill/mqperf/blob/master/src/main/scala/com/softwaremill/mqperf/mq/RabbitMq.scala) implementation of the Mq interface is again pretty straightforward. We are using the mentioned publisher confirms, and setting the quality-of-service when receiving so that at most 100 messages are delivered unconfirmed (in-flight).

An important side-node: RabbitMQ has a great web-based console, available with almost no setup, which offers some very good insights into how the queue is performing.

# ActiveMQ Artemis

<table>
  <tbody>
    <tr>
      <td><em>Version</em></td>
      <td>2.15.0, java driver 2.15.0</td>
    </tr>
    <tr>
      <td><em>Replication</em></td>
      <td>synchronous</td>
    </tr>
    <tr>
      <td><em>Replication type</em></td>
      <td>active-passive</td>
    </tr>
  </tbody>
</table>

[Artemis](http://activemq.apache.org/artemis/) is the successor to popular ActiveMQ 5 (which hasn’t seen any significant development lately). Artemis emerged from a donation of the [HornetQ](http://hornetq.jboss.org) code to Apache, and is being developed by both RedHat and ActiveMQ developers. Like RabbitMQ, it supports AMQP, as well as other messaging protocols, for example STOMP and MQTT.

Artemis supports a couple of high availability deployment options, either using [replication or a shared store](https://activemq.apache.org/components/artemis/documentation/latest/ha.html). We’ll be using the over-the-network setup, that is replication.

Unlike other tested brokers, Artemis replicates data to one backup node. The basic unit here is a **live-backup pair**. The backup happens synchronously, that is a message is considered sent only when it is replicated to the other server. Failover and failback can be configured to happen automatically, without operator intervention.

Moreover, queues in Artemis can be sharded across multiple live-backup pairs. That is, we can deploy a couple of such pairs and use them as a single cluster. As we aren’t able to create a three-node cluster, instead we’ll use a six-node setup in a “star” configuration: three live (leader) servers, all of which serve traffic of the queue used for tests. Each of them has a backup server.

Split-brain issues are addressed by an implementation of [quorum voting](https://activemq.apache.org/components/artemis/documentation/latest/network-isolation.html). This is similar to what we’ve seen e.g. in the RabbitMQ implementation.

The Artemis test client code is based on JMS, and doesn’t contain any Artmis-specific code - uses only standard JMS concepts - sending messages, receiving and transactions. We only need to use an Artemis-specific connection factory, see [ArtemisMq](https://github.com/softwaremill/mqperf/blob/master/src/main/scala/com/softwaremill/mqperf/mq/ArtemisMq.scala).

The [configuration changes](https://github.com/softwaremill/mqperf/blob/master/ansible/roles/artemis/templates/broker.xml.j2) comparing to the default are:

* the `Xmx` java parameter bumped to `48G`
* in `broker.xml`, the `global-max-size` setting changed to `48G`
* `journal-type` set to `MAPPED`
* `journal-datasync`, `journal-sync-non-transactional` and `journal-sync-transactional` all set to `false`

Performance wise, Artemis does very well. Our baseline single-thread setup achieves **13 650 msgs/s**. By adding nodes, we can scale that result to **52 820 msgs/s** using 4 sending nodes, 8 receiver nodes each running 25 threads:

![Artemis](artemis.png)

In that last case, the 95th percentile of send latency is a stable **48 ms** and maximum processing latency of **49 ms**:

<a href="https://snapshot.raintank.io/dashboard/snapshot/B8uS0BtsxL19pop34pB7HwRIaf0ljQ72" target="_blank">
  ![Artemis grafana](artemis%20grafana.png) 
</a>

However, as the Artemis team [noted](https://github.com/softwaremill/mqperf/pull/58), the addressing model currently implemented in Artemis isn't the best fit for the mqperf benchmark. Consuming messages from a single queue on a single broker is basically a single-thread process - which on one hand ensures that messages are consumed in-order, but on the other prevents scaling as more consumers are added (quite the contrary!). This can be alleviated by using dedicated queues for consumers, or broadcast topics with filtering, however we then need application-side coordination code which assigns queues to consumers, ensures that there's at least one consumer for each queue, and performs rebalancing on failure.

Here are all of the results:

<table>
  <thead>
    <tr>
      <th>Threads</th>
      <th>Sender nodes</th>
      <th>Receiver nodes</th>
      <th>Send msgs/s</th>
      <th>Receive msgs/s</th>
      <th>Processing latency</th>
      <th>Send latency</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>1</td>
      <td>1</td>
      <td>2</td>
      <td>13 647,00</td>
      <td>13 648,00</td>
      <td>48,00</td>
      <td>44,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>1</td>
      <td>2</td>
      <td>36 035,00</td>
      <td>36 021,00</td>
      <td>47,00</td>
      <td>46,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>1</td>
      <td>2</td>
      <td>43 643,00</td>
      <td>43 630,00</td>
      <td>48,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>1</td>
      <td>2</td>
      <td>4</td>
      <td>21 380,00</td>
      <td>21 379,00</td>
      <td>47,00</td>
      <td>45,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>2</td>
      <td>4</td>
      <td>39 320,00</td>
      <td>39 316,00</td>
      <td>48,00</td>
      <td>47,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>2</td>
      <td>4</td>
      <td>51 089,00</td>
      <td>51 090,00</td>
      <td>48,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>1</td>
      <td>4</td>
      <td>8</td>
      <td>35 538,00</td>
      <td>35 525,00</td>
      <td>47,00</td>
      <td>46,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>4</td>
      <td>8</td>
      <td>42 881,00</td>
      <td>42 882,00</td>
      <td>48,00</td>
      <td>47,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>4</td>
      <td>8</td>
      <td>52 820,00</td>
      <td>52 826,00</td>
      <td>49,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>6</td>
      <td>12</td>
      <td>44 435,00</td>
      <td>44 436,00</td>
      <td>48,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>6</td>
      <td>12</td>
      <td>49 279,00</td>
      <td>49 278,00</td>
      <td>77,00</td>
      <td>48,00</td>
    </tr>
  </tbody>
</table>

Artemis also offers a web console which helps to visualise the current cluster state.

![Artemis dashboard](artemis%20dashboard.png)

# NATS Streaming

<table>
  <tbody>
    <tr>
      <td><em>Version</em></td>
      <td>0.19.0, java driver 2.2.3</td>
    </tr>
    <tr>
      <td><em>Replication</em></td>
      <td>synchronous</td>
    </tr>
    <tr>
      <td><em>Replication type</em></td>
      <td>active-passive</td>
    </tr>
  </tbody>
</table>

[NATS](https://nats.io) is a lightweight messaging system, popular especially in the domain of IoT applications. It supports a number of communication patterns, such as request-reply, publish-subscribe and wildcard subscriptions. NATS offers clients in most popular languages, as well as integrations with many external systems. However, in itself, NATS doesn’t offer clustering, replication or message acknowledgments.

For that purpose, [NATS Streaming](https://docs.nats.io/nats-streaming-concepts/intro) builds upon NATS, providing support for replicated, persistent messaging, durable subscriptions and, using acknowledgements, guarantees at-least-once delivery. It embeds a NATS server, extending its protocol with additional capabilities.

A NATS Streaming server stores an **ever-growing log of messages**, which are deleted after reaching the configured size, message count or message age limit (in this respect, the design is similar to a Kafka topic’s retention policy). The server is simple to setup - not a lot of configuration is needed. The client APIs are similarly straightforward to use.

As with other queue implementations discussed previously, NATS Streaming uses the **Raft protocol** for replicating data in a cluster. A write is successful only after a successful consensus round - when the majority of nodes accept it. Hence, this design should be resilient against split-brain scenarios.

There’s a single leader node, which accepts writes. This means (as the documentation [emphasises](https://docs.nats.io/nats-streaming-concepts/clustering)), that this setup isn’t horizontally scalable. An alternate version of a NATS-based clustered system - [JetStream](https://github.com/nats-io/jetstream) is being developed, which promises horizontal scalability.

What’s interesting is a [whole section](https://docs.nats.io/developing-with-nats-streaming/streaming) in the docs dedicated to the use-cases of at-least-once, persistent messaging - when to use it, and more importantly, when not to use it:

> Just be aware that using an at least once guarantee is the facet of messaging with the highest cost in terms of compute and storage. The NATS Maintainers highly recommend a strategy of defaulting to core NATS using a service pattern (request/reply) to guarantee delivery at the application level and using streaming only when necessary.

It’s always good to consider your architectural requirements, but in our tests of course we’ll focus on the replicated & persistent setup. Speaking of tests, our baseline test achieved **1 725 msgs/s**. This scales up to **27 400 msgs/s** when using 25 threads on 6 senders nodes, and 12 receiver nodes.

![NATS Streaming](nats.png)

Latencies are also looking good, with 95th send percentile being at most **95 ms**, while messages have been usually processed within **148 ms**.

<a href="https://snapshot.raintank.io/dashboard/snapshot/xIInK5XsACVZO2VS0RxYhHapJxKDrXrS" target="_blank">
    ![NATS Streaming grafana](nats%20grafana.png)
</a>

Here’s a summary of the test runs:

<table>
  <thead>
    <tr>
      <th>Threads</th>
      <th>Sender nodes</th>
      <th>Receiver nodes</th>
      <th>Send msgs/s</th>
      <th>Receive msgs/s</th>
      <th>Processing latency</th>
      <th>Send latency</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>1</td>
      <td>1</td>
      <td>2</td>
      <td>1 725,00</td>
      <td>1 725,00</td>
      <td>105,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>1</td>
      <td>2</td>
      <td>3 976,00</td>
      <td>3 995,00</td>
      <td>142,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>1</td>
      <td>2</td>
      <td>10 642,00</td>
      <td>10 657,00</td>
      <td>145,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>1</td>
      <td>2</td>
      <td>4</td>
      <td>1 870,00</td>
      <td>1 870,00</td>
      <td>138,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>2</td>
      <td>4</td>
      <td>5 958,00</td>
      <td>5 957,00</td>
      <td>143,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>2</td>
      <td>4</td>
      <td>18 023,00</td>
      <td>18 026,00</td>
      <td>145,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>1</td>
      <td>4</td>
      <td>8</td>
      <td>3 379,00</td>
      <td>3 377,00</td>
      <td>143,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>4</td>
      <td>8</td>
      <td>10 014,00</td>
      <td>10 015,00</td>
      <td>145,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>4</td>
      <td>8</td>
      <td>24 834,00</td>
      <td>24 828,00</td>
      <td>146,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>6</td>
      <td>12</td>
      <td>27 392,00</td>
      <td>27 388,00</td>
      <td>147,00</td>
      <td>75,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>8</td>
      <td>16</td>
      <td>26 699,00</td>
      <td>26 696,00</td>
      <td>148,00</td>
      <td>95,00</td>
    </tr>
  </tbody>
</table>

# Redis Streams

<table>
  <tbody>
    <tr>
      <td><em>Version</em></td>
      <td>6.2.4, jedis client 3.6.1</td>
    </tr>
    <tr>
      <td><em>Replication</em></td>
      <td>active-passive</td>
    </tr>
    <tr>
      <td><em>Replication type</em></td>
      <td>asynchronous</td>
    </tr>
  </tbody>
</table>

[Redis](https://redis.io) is probably best known as a really fast, and really useful key-value cache/database. It might be less known, that Redis supports both [persistence](https://redis.io/topics/persistence) and [replication](https://redis.io/topics/replication), as well as fail-over and sharding using [cluster](https://redis.io/topics/cluster-tutorial).

However, Redis also offers a streaming component. The logical design borrows some concepts from Kafka (such as consumer groups), however the internal implementation in entirely different. The documentation includes a [comprehensive tutorial](https://redis.io/topics/streams-intro), providing usage guidelines and detailing the design, along with its limitations.

Using streams with Redis is implemented using the `XADD`, `XRANGE`, `XREAD`, `XREADGROUP` and `XACK` commands. In addition to the basic operation of adding an element to a stream, it offers three basic modes of accessing data:

* range scans, to read an arbitrary stream element or elements
* fan-out reads, where every consumer reads every message (topic semantics)
* consumer group reads, where every consumer reads a dedicated set of messages (queue semantics)

We'll be using the consumer group functionality. Each consumer group and each consumer within a group is identified by a unique identifier. To receive a message, a consumer needs to issue the `XREADGROUP` command with the stream name, consumer group id and consumer id. When a message is processed, it needs to be acknowledged using `XACK`.

For each stream and consumer group, Redis maintains server-side state which determines which consumer received which messages, which messages are not yet received by any consumer, and which have been acknowledged. What's important, is that consumer ids have to be managed by the application. This means that if a consumer with a given id goes offline permanently, it's possible that some messages will get stuck in a received, but not acknowledged state. To remedy the situation, other consumers should periodically issue a `XAUTOCLAIM` command, which reassigns messages, if they haven't been processed for the given amount of time. This is a mechanism similar to SQS's visibility timeouts, however initiated by the client, not the server.

Moreover, after a consumer restarts, it should first check if there are some unacknowledged messages which are assigned to its id. If so, they should be reprocessed. Combined with auto-claiming, we get an implementation of at-least-once delivery. Unlike in Kafka or other messaging systems, the clients need to take care and implement this correctly to make sure no messages are lost.  

Replication is Redis is asynchronous, unless we use the `WAIT` command after each operation to make sure it's propagated across the cluster. We won't be using this option in our tests, as it goes against the way Redis should be used and even the documentation states that it will make the system very slow. Hence, upon failure some data loss is possible. Note that it is recommended to have persistence enabled when using replication, as otherwise it's possible to have the entire state truncated upon a node restart.

Persistence, by default flushes data to disk asynchronously (every second), but this can be configured to flush after each command - however again, causing a huge performance penalty.

Additional features of Redis Streams include message delivery counters (allowing implementing a dead letter queue), observability commands and specifying a maximum number of elements in a stream, truncating the stream if that limit is exceeded. What's worth noting is a dedicated section in the documentation, explicitly stating the features and limitations of the persistence & replication system, clearly stating when data loss might occur. This leaves no doubt when choosing the right tradeoffs in a system's design.

Finally, let's focus on scaling Redis Streams. All of the streaming operations above operate on a single Redis key, residing on a single Redis master server (operations are then replicated to slaves). What if we'd like to scale our system above that? One solution is to use Redis Cluster and multiple stream keys. When sending data, we then have to choose a stream key, either randomly or in some deterministic fashion. This resembles Kafka's partitions and partition keys. On the consumer side, we might consume from all keys at once; we could also have dedicated consumers for keys, but then we'd need some way to maintain a cluster-wide view of the consumer <-> key association, to ensure that each key is consumer by some consumer, which isn't an easy task. The number of keys also needs to be large enough to ensure that they are evenly distributed across the shards (distribution is based on key hash values). 

Let's look at the performance test results. A 3-node active-passive setup achieved up to **41 600 msgs/s**:

<a href="https://snapshot.raintank.io/dashboard/snapshot/WnfdD4OMvylP7DD03p5E26U28Yq4qtqv" target="_blank">
    ![Redis streams grafana](redis%20streams%20grafana.png)
</a>

However, when we employ a sharded cluster of 9 nodes, that is 3x(master + 2 replicas), and with 100 stream keys, we can get up to **84 000 msgs/s**, however with quite high latencies:

<a href="https://snapshot.raintank.io/dashboard/snapshot/tVU9M1swJh3RsYH0fQGHr0JUW1JcR7eS " target="_blank">
    ![Redis streams 9 grafana](redis%20streams%209%20grafana.png)
</a>

Here are the test results in full:

<table>
  <thead>
    <tr>
      <th>Threads</th>
      <th>Sender nodes</th>
      <th>Receiver nodes</th>
      <th>Send msgs/s</th>
      <th>Receive msgs/s</th>
      <th>Processing latency</th>
      <th>Send latency</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>1</td>
      <td>2</td>
      <td>4</td>
      <td>20 114</td>
      <td>20 116</td>
      <td>45</td>
      <td>45</td>
    </tr>
    <tr>
      <td>10</td>
      <td>2</td>
      <td>4</td>
      <td>32 879</td>
      <td>32 878</td>
      <td>47</td>
      <td>47</td>
    </tr>
    <tr>
      <td>10</td>
      <td>6</td>
      <td>6</td>
      <td>39 792</td>
      <td>38 796</td>
      <td>48</td>
      <td>48</td>
    </tr>
    <tr>
      <td>10/15</td>
      <td>8</td>
      <td>12</td>
      <td>39 744</td>
      <td>39 743</td>
      <td>48</td>
      <td>48</td>
    </tr>
    <tr>
      <td>20/15</td>
      <td>8</td>
      <td>12</td>
      <td>39 387</td>
      <td>39 391</td>
      <td>48</td>
      <td>48</td>
    </tr>
    <tr>
      <td>60/15</td>
      <td>8</td>
      <td>12</td>
      <td>42 750</td>
      <td>42 748</td>
      <td>108</td>
      <td>137</td>
    </tr>
    <tr>
      <td>80/15</td>
      <td>8</td>
      <td>12</td>
      <td>41 592</td>
      <td>41 628</td>
      <td>144</td>
      <td>178</td>
    </tr>
  </tbody>
</table>

# Pulsar

<table>
  <tbody>
    <tr>
      <td><em>Version</em></td>
      <td>2.6.2</td>
    </tr>
    <tr>
      <td><em>Replication</em></td>
      <td>configurable, asynchronous &amp; synchronous</td>
    </tr>
    <tr>
      <td><em>Replication type</em></td>
      <td>active-active</td>
    </tr>
  </tbody>
</table>

[Apache Pulsar](https://pulsar.apache.org) is a distributed streaming and messaging platform. It is often positioned in a similar segment as Apache Kafka, and the two platforms are often [compared and contrasted](https://blog.softwaremill.com/comparing-apache-kafka-and-apache-pulsar-3bd44e00f304).

Pulsar was initially developed at Yahoo!, and now continues to evolve as an open-source project. It builds upon two other Apache projects:

* [ZooKeeper](https://zookeeper.apache.org) for cluster discovery and coordination
* [BookKeeper](https://bookkeeper.apache.org) as the replicated storage service

A Pulsar deployment consists of nodes which take on one of three nodes:

* bookie: handles persistent storage of messages
* broker: a stateless service which accepts messages from producers, dispatches messages to consumers and communicates with bookies to store data
* zookeeper: which provides coordination services for the above two

Hence, a minimal deployment should in fact consist of more than 3 nodes (although we can colocate a couple of roles on a single machine). For our tests we have decided to use separate machines for separate roles, and hence we ended up with 3 zookeeper nodes, 3 bookie nodes and 2 broker nodes.

When working with Pulsar, we’re dealing with [three main concepts](https://pulsar.apache.org/docs/en/concepts-messaging): **messages, topics and subscriptions**. Producers send messages to topics, either individually or in batches. Consumers can subscribe to a topic in four modes: `exclusive`, `failover`, `shared` and `key_shared`, providing a subscription name.

Combining a shared or unique **subscription name**, with one of the four **consumption modes**, we can achieve pub-sub topics, message queues, or a combination of these behaviours. Pulsar is very flexible in this regard.

Messages in Pulsar are deleted after they are acknowledged, and this is tracked per-subscription. That is, if there are no subscribers to a topic, messages will be marked for deletion right after being sent. Acknowledging a message in one subscription doesn’t affect other subscriptions. Additionally, we can specify a message retention policy, to keep messages for a longer time.

Moreover, topics can be **partitioned**. Behind the scenes, Pulsar creates an internal topic for every partition (these partitions are something quite different than in Kafka!). However, from the producers and consumers point of view such a topic behaves as a single one. As a single topic is always handled by a single broker, increasing the number of partitions, we can increase throughput by allowing multiple brokers to accept and dispatch messages.

As mentioned above, all storage is handled by Apache BookKeeper. Entries (messages) are stored in sequences called **ledgers**. We can configure how many copies of a ledger are created (`managedLedgerDefaultEnsembleSize`), in how many copies a message is stored (`managedLedgerDefaultWriteQuorum`) and how many nodes have to acknowledge a write (`managedLedgerDefaultAckQuorum`). Following our persistency requirements, we’ve been using 3 ledger copies, and requiring at least 2 copies of each message.

The setting above corresponds to synchronous replication, but by setting the quorum to 1 or 0, we would get an asynchronous one.

Unlike previously discussed queues, pulsar is an **active-active system**: that is, every node is equal and can handle user requests. Coordination is performed via Zookeeper, which also secures the cluster against split-brain problems.

Pulsar offers a number of additional features, such as Pulsar Functions, SQL, transactions, geo replication, multi-tenancy, connectors to many popular data processing systems (Pulsar IO), a schema registry and others.

Performance-wise, it shows that each node can handle messaging traffic. A baseline setup using a single partition achieves **1 300 msgs/s**. Using 8 sender and 16 receiver nodes, each running 25 threads, we get **147 000 msgs/s**.

However, we can also increase the number of partitions, thus increasing concurrency. We achieved the best results using 4 partitions (that is, a single broker was handling 2 partitions on average); adding more partitions didn’t further increase performance. Here, we got up to **358 000 msgs/s** using 8 sender nodes each running 100 threads, and 16 receiver nodes each running 25 threads.

![Pulsar](pulsar.png)

Send latencies are stable, and the 95th percentile is **48 ms**. Processing latencies vary from **48 ms**, to at most **214 ms** in the test which achieved highest throughput.

<a href="https://snapshot.raintank.io/dashboard/snapshot/fiQeLTljkKFRs7TdGgkly3aZMKxIktt9" target="_blank">
    ![Pulsar grafana](pulsar%20grafana.png)
</a>

Here are the full test results, for 1 partition:

<table>
  <thead>
    <tr>
      <th>Threads</th>
      <th>Sender nodes</th>
      <th>Receiver nodes</th>
      <th>Send msgs/s</th>
      <th>Receive msgs/s</th>
      <th>Processing latency</th>
      <th>Send latency</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>1</td>
      <td>1</td>
      <td>2</td>
      <td>1 298,00</td>
      <td>1 298,00</td>
      <td>48,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>1</td>
      <td>2</td>
      <td>6 711,00</td>
      <td>6 711,00</td>
      <td>112,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>1</td>
      <td>2</td>
      <td>31 497,00</td>
      <td>31 527,00</td>
      <td>48,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>1</td>
      <td>2</td>
      <td>4</td>
      <td>2 652,00</td>
      <td>2 652,00</td>
      <td>48,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>2</td>
      <td>4</td>
      <td>12 787,00</td>
      <td>12 789,00</td>
      <td>107,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>2</td>
      <td>4</td>
      <td>55 621,00</td>
      <td>55 677,00</td>
      <td>50,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>1</td>
      <td>4</td>
      <td>8</td>
      <td>5 156,00</td>
      <td>5 156,00</td>
      <td>72,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>4</td>
      <td>8</td>
      <td>24 048,00</td>
      <td>24 054,00</td>
      <td>94,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>4</td>
      <td>8</td>
      <td>96 154,00</td>
      <td>96 272,00</td>
      <td>50,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>6</td>
      <td>12</td>
      <td>124 152,00</td>
      <td>124 273,00</td>
      <td>50,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>50</td>
      <td>6</td>
      <td>12</td>
      <td>160 237,00</td>
      <td>160 254,00</td>
      <td>102,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>8</td>
      <td>16</td>
      <td>147 348,00</td>
      <td>147 405,00</td>
      <td>50,00</td>
      <td>48,00</td>
    </tr>
  </tbody>
</table>

And using 4 partitions:

<table>
  <thead>
    <tr>
      <th>Threads</th>
      <th>Sender nodes</th>
      <th>Receiver nodes</th>
      <th>Send msgs/s</th>
      <th>Receive msgs/s</th>
      <th>Processing latency</th>
      <th>Send latency</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>1</td>
      <td>4</td>
      <td>8</td>
      <td>5 248,00</td>
      <td>5 237,00</td>
      <td>61,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>4</td>
      <td>8</td>
      <td>102 821,00</td>
      <td>102 965,00</td>
      <td>50,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>6</td>
      <td>12</td>
      <td>141 462,00</td>
      <td>141 977,00</td>
      <td>50,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>50</td>
      <td>6</td>
      <td>12</td>
      <td>228 875,00</td>
      <td>228 958,00</td>
      <td>73,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>8</td>
      <td>16</td>
      <td>176 439,00</td>
      <td>176 388,00</td>
      <td>50,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>50</td>
      <td>8</td>
      <td>16</td>
      <td>259 133,00</td>
      <td>259 203,00</td>
      <td>64,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>75/25</td>
      <td>8</td>
      <td>16</td>
      <td>333 622,00</td>
      <td>333 643,00</td>
      <td>65,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>100/25</td>
      <td>8</td>
      <td>16</td>
      <td>358 323,00</td>
      <td>358 648,00</td>
      <td>214,00</td>
      <td>49,00</td>
    </tr>
    <tr>
      <td>50</td>
      <td>10</td>
      <td>20</td>
      <td>260 070,00</td>
      <td>260 165,00</td>
      <td>94,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>100/25</td>
      <td>10</td>
      <td>16</td>
      <td>320 853,00</td>
      <td>320 315,00</td>
      <td>2 698,00</td>
      <td>49,00</td>
    </tr>
  </tbody>
</table>

# RocketMQ

<table>
  <tbody>
    <tr>
      <td><em>Version</em></td>
      <td>4.7.1</td>
    </tr>
    <tr>
      <td><em>Replication</em></td>
      <td>configurable, asynchronous &amp; synchronous</td>
    </tr>
    <tr>
      <td><em>Replication type</em></td>
      <td>active-passive</td>
    </tr>
  </tbody>
</table>

[RocketMQ](https://rocketmq.apache.org) is a unified messaging engine and lightweight data processing platform. The message broker was initially created as a replacement for ActiveMQ 5 (not the Artemis version we discussed before, but its predecessor). It aims to support similar use-cases, provides JMS and native interfaces (among others), and puts a focus on performance.

There are three node types from which a RocketMQ cluster is created:

* broker master, which accepts client connections, receives and sends messages
* broker slave, which replicates data from the master
* name server, which provides service discovery and routing

Each broker cluster can work in synchronous or asynchronous replication modes, which is configured on the broker level. In our tests, we’ve been using synchronous replication.

In theory, it should be possible to deploy a broker cluster with a single master and two slaves, to achieve a replication factor of 3. However, we couldn’t get this setup to work. Hence instead, we’ve settled on a similar configuration as with ActiveMQ Artemis - **three copies of master-slave** pairs. Like with Artemis, a queue can be deployed on multiple brokers, and the messages are sharded/load-balanced when producing and consuming from the topic.

Additionally, we’ve deployed a single name server, but in production deployments, this component should be clustered as well, with a minimum of three nodes.

Speaking of topics, RocketMQ supports both pub-sub topics, as well as typical message queues, where each message is consumed by a single consumer.  This corresponds to `BROADCAST` and `CLUSTERING` message consumption modes. Moreover, messages can be consumed in-order, or concurrently (we’ve been using the latter option).

Messages are consumed and acknowledged per **consumer-group**, which is specified when configuring the consumer. When creating a new consumer group, historical messages can be received, as long as they are still available; by default, RocketMQ retains messages for 2 days.

RocketMQ supports transactions, however there’s no built-in deduplication. Moreover, the documentation is quite basic, making this system a bit challenging to setup and understand. There’s no mention if and which consensus algorithm is used, and if split-brain scenarios are in any way addressed; however, there is a recommendation to deploy at least 3 name servers, which would hint at a quorum-based approach.

However, RocketMQ definitely makes up for these deficiencies in performance. Our baseline test with a single sender and 1 thread achieved **13 600 msgs/s**. However, processing latency was quite large in that particular test - 37 seconds. It’s quite easy to overwhelm RocketMQ with sends so that the receiver threads can’t keep up. The most we’ve been able to achieve where sends are receives are on par is with 4 sender nodes, 4 receiver nodes running 25 threads each. In that case, the broker processed **485 000 msgs/s**.

![RocketMQ](rocketmq.png)

Send latencies are always within **44-47ms**, however as mentioned, processing latencies get high pretty quickly. The highest throughput with reasonable processing latencies (**162 ms**) achieved **129 100 msgs/s**.

<a href="https://snapshot.raintank.io/dashboard/snapshot/7aGk4raOj9lhTfjDXGslhqlA79uOAegn" target="_blank">
    ![RocketMQ grafana](rocketmq%20grafana.png)
</a>

Here’s a summary of our tests:

<table>
  <thead>
    <tr>
      <th>Threads</th>
      <th>Sender nodes</th>
      <th>Receiver nodes</th>
      <th>Send msgs/s</th>
      <th>Receive msgs/s</th>
      <th>Processing latency</th>
      <th>Send latency</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>1</td>
      <td>1</td>
      <td>2</td>
      <td>13 605,00</td>
      <td>14 183,00</td>
      <td>37 056,00</td>
      <td>44,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>1</td>
      <td>2</td>
      <td>64 638,00</td>
      <td>64 635,00</td>
      <td>94,00</td>
      <td>44,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>1</td>
      <td>2</td>
      <td>260 093,00</td>
      <td>252 308,00</td>
      <td>18 859,00</td>
      <td>45,00</td>
    </tr>
    <tr>
      <td>1</td>
      <td>2</td>
      <td>4</td>
      <td>29 076,00</td>
      <td>29 075,00</td>
      <td>135,00</td>
      <td>43,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>2</td>
      <td>4</td>
      <td>129 106,00</td>
      <td>129 097,00</td>
      <td>162,00</td>
      <td>44,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>2</td>
      <td>4</td>
      <td>411 923,00</td>
      <td>410 891,00</td>
      <td>17 436,00</td>
      <td>46,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>3</td>
      <td>6</td>
      <td>451 454,00</td>
      <td>422 619,00</td>
      <td>60 000,00</td>
      <td>46,00</td>
    </tr>
    <tr>
      <td>1</td>
      <td>4</td>
      <td>8</td>
      <td>55 662,00</td>
      <td>55 667,00</td>
      <td>960,00</td>
      <td>44,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>4</td>
      <td>8</td>
      <td>202 110,00</td>
      <td>147 859,00</td>
      <td>60 000,00</td>
      <td>45,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>4</td>
      <td>4</td>
      <td>485 322,00</td>
      <td>416 900,00</td>
      <td>60 000,00</td>
      <td>47,00</td>
    </tr>
  </tbody>
</table>

# Kafka

<table>
  <tbody>
    <tr>
      <td><em>Version</em></td>
      <td>2.6.0</td>
    </tr>
    <tr>
      <td><em>Replication</em></td>
      <td>configurable, asynchronous &amp; synchronous</td>
    </tr>
    <tr>
      <td><em>Replication type</em></td>
      <td>active-active</td>
    </tr>
  </tbody>
</table>

[Kafka](https://kafka.apache.org) is a distributed event-streaming platform. It is widely deployed and has gained considerable popularity in recent years. Originally developed at LinkedIn, it is now an open-source project, with commercial extensions and support offered by [Confluent](https://www.confluent.io).

A Kafka cluster consists of a number of **broker nodes**, which handle persistence, replication, client connections: they both accept and send messages. In addition, there’s a **ZooKeeper cluster** which is used for service discovery and coordination. However, there are plans to replace that component with one built directly into the Kafka broker.

Kafka takes a different approach to messaging, compared to what we’ve seen before. The server itself is a streaming publish-subscribe system, or at an even more basic level, a distributed log. Each Kafka topic can have multiple partitions; by using more partitions, the consumers of the messages (and the throughput) may be scaled and concurrency of processing increased. It’s not uncommon for a topic to have 10s or 100s of partitions.

On top of the publish-subscribe system, which persists messages within partitions, point-to-point messaging (queueing) is built, by putting a **significant amount of logic into the consumers**. This again contrasts Kafka when comparing with other messaging systems we've looked at: there, usually it was the server that contained most of the message-consumed-by-one-consumer logic. Here it's the consumer.

Each consumer in a **consumer group** reads messages from a number of dedicated partitions; hence it doesn't make sense to have more consumer threads than partitions. Or in other words, a single partition is consumed by exactly one consumer within a consumer group (as long as there are any consumers).

Messages aren't acknowledged on the server (which is a very important design difference!), but instead processed message offsets are managed by consumers and written per-parition back to a special Kafka store (or a client-specific store), either automatically in the background, or manually. This allows Kafka to achieve much better performance.

Such a design has a couple of consequences:

* only messages from each partition are processed in-order. A custom partition-routing strategy can be defined
* all consumers should consume messages at the same speed. Messages from a slow consumer won't be "taken over" by a fast consumer
* messages are acknowledged "up to" an offset. That is messages can't be selectively acknowledged.
* no "advanced" messaging options are available, such as routing or delaying message delivery.

You can read more about the design of the consumer in [Kafka's docs](http://kafka.apache.org/documentation.html), which are quite comprehensive and provide a good starting point when setting up the broker.

It is also possible to add a layer on top of Kafka to implement individual message acknowledgments and re-delivery, see [our article on the performance of kmq](https://softwaremill.com/kafka-with-selective-acknowledgments-performance/) and the [KMQ](https://github.com/softwaremill/kmq) project. This scheme uses an additional topic to track message acknowledgements. In case a message isn’t acknowledged within specified time, it is re-delivered. This is quite similar to how SQS works. When testing Kafka, we’ve primarily tested “vanilla” Kafka, but also included a KMQ test for comparison.

To achieve guaranteed sends and at-least-once delivery, we used the following configuration (see the [KafkaMq class](https://github.com/softwaremill/mqperf/blob/master/src/main/scala/com/softwaremill/mqperf/mq/KafkaMq.scala)):

* topic is created with a `replication-factor` of `3`
* for the sender, the `request.required.acks` option is set to `-1` (synchronous replication; in conjunction with `min.insync.replicas` topic config set to `2` a send request blocks until it is accepted by at least 2 replicas - a quorum when we have 3 nodes in total). If you'd like asynchronous replication, this can be set to `1` (a send request blocks until it is accepted by the partition leader)
* consumer offsets are committed every 10 seconds manually; during that time, message receiving is blocked (a read-write lock is used to assure that). That way we can achieve at-least-once delivery (only committing when messages have been "observed").

It’s important to get the above configuration right. You can read more about proper no-data-loss Kafka configuration [on our blog](https://blog.softwaremill.com/help-kafka-ate-my-data-ae2e5d3e6576), as well as how to [guarantee message ordering](https://blog.softwaremill.com/does-kafka-really-guarantee-the-order-of-messages-3ca849fd19d2): by default, even within a partition, messages might be reordered!

As Kafka uses ZooKeeper, network partitions are handled at that level. Kafka has a number of features which are useful when designing a messaging or data streaming system, such as deduplication, transactions, a SQL interface, connectors to multiple popular data processing systems, a schema registry and a streaming framework with in-Kafka exactly-once processing guarantees.

Let’s look at the performance tests. Here, Kafka has no equals, the numbers are impressive. A baseline test achieved around **7 000 msgs/s**. Using 8 sender nodes and 16 receiver nodes, running 25 threads each, we can achieve **270 000 msgs/s**.

However, we didn’t stop here. It turns out that the sending part is the bottleneck (and it might not be surprising, as that’s where most coordination happens: we wait for messages to be persisted and acknowledged; while on the receiver side, we allow asynchronous, periodic offset commits). By using 200 threads on 16 sender nodes, with 16 receiver nodes, but running only 5 threads each, we achieved **828 000 msgs/s**.

![Kafka](kafka.png)

We’ve been using at least 64 partitions for the tests, scaling this up if there were more total receiver threads, to 80 or 100 partitions.

What about latencies? They are very stable, even under high load. 95th percentile of **both** send and receives latencies is steadily at **48 ms**. Here’s the dashboard from the test run with the biggest throughput:

<a href="https://snapshot.raintank.io/dashboard/snapshot/wya8hCjrVGe2FCVZUggbnSlkdi2dY1T1" target="_blank">
    ![Kafka grafana](kafka%20grafana.png)
</a>

As mentioned before, we’ve also tested a setup with selective message acknowledgments, using KMQ (the implementation is [here](https://github.com/softwaremill/mqperf/blob/master/src/main/scala/com/softwaremill/mqperf/mq/KmqMq.scala)). Adding another topic for tracking redeliveries, and performing additional message marker sends did impact performance, but not that much. Using 100 threads on 20 senders, and 5 threads on 16 senders, we’ve achieved a throughput of **676 800 msgs/s**. However, processing latencies went up to about **1 812 ms**:

<a href="https://snapshot.raintank.io/dashboard/snapshot/XDUgTNnW6fuGju44Iip0PPHH1MEPWlLI?orgId=2" target="_blank">
    ![KMQ grafana](kmq%20grafana.png)
</a>

Finally, here are our Kafka test results in full:

<table>
  <thead>
    <tr>
      <th>Threads</th>
      <th>Sender nodes</th>
      <th>Receiver nodes</th>
      <th>Send msgs/s</th>
      <th>Receive msgs/s</th>
      <th>Processing latency</th>
      <th>Send latency</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>1</td>
      <td>1</td>
      <td>2</td>
      <td>7 458,00</td>
      <td>7 463,00</td>
      <td>47,00</td>
      <td>47,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>1</td>
      <td>2</td>
      <td>31 350,00</td>
      <td>31 361,00</td>
      <td>47,00</td>
      <td>47,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>1</td>
      <td>2</td>
      <td>92 373,00</td>
      <td>92 331,00</td>
      <td>81,00</td>
      <td>47,00</td>
    </tr>
    <tr>
      <td>1</td>
      <td>2</td>
      <td>4</td>
      <td>15 184,00</td>
      <td>15 175,00</td>
      <td>47,00</td>
      <td>47,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>2</td>
      <td>4</td>
      <td>55 402,00</td>
      <td>55 355,00</td>
      <td>47,00</td>
      <td>47,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>2</td>
      <td>4</td>
      <td>127 274,00</td>
      <td>127 345,00</td>
      <td>50,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>1</td>
      <td>4</td>
      <td>8</td>
      <td>27 044,00</td>
      <td>27 045,00</td>
      <td>47,00</td>
      <td>47,00</td>
    </tr>
    <tr>
      <td>5</td>
      <td>4</td>
      <td>8</td>
      <td>84 234,00</td>
      <td>84 223,00</td>
      <td>48,00</td>
      <td>47,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>4</td>
      <td>8</td>
      <td>188 557,00</td>
      <td>188 524,00</td>
      <td>48,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>6</td>
      <td>12</td>
      <td>233 379,00</td>
      <td>233 228,00</td>
      <td>48,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25</td>
      <td>8</td>
      <td>16</td>
      <td>272 828,00</td>
      <td>272 705,00</td>
      <td>48,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25/5</td>
      <td>8</td>
      <td>16</td>
      <td>235 782,00</td>
      <td>235 802,00</td>
      <td>48,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>50/5</td>
      <td>8</td>
      <td>16</td>
      <td>338 591,00</td>
      <td>338 614,00</td>
      <td>48,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>75/5</td>
      <td>8</td>
      <td>16</td>
      <td>432 049,00</td>
      <td>432 071,00</td>
      <td>48,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>100/5</td>
      <td>8</td>
      <td>16</td>
      <td>498 528,00</td>
      <td>498 498,00</td>
      <td>48,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>25/5</td>
      <td>10</td>
      <td>20</td>
      <td>245 284,00</td>
      <td>245 304,00</td>
      <td>48,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>50/5</td>
      <td>16</td>
      <td>16</td>
      <td>507 393,00</td>
      <td>507 475,00</td>
      <td>48,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>100/5</td>
      <td>16</td>
      <td>16</td>
      <td>678 255,00</td>
      <td>678 279,00</td>
      <td>48,00</td>
      <td>48,00</td>
    </tr>
    <tr>
      <td>150/5</td>
      <td>16</td>
      <td>16</td>
      <td>745 203,00</td>
      <td>745 163,00</td>
      <td>49,00</td>
      <td>49,00</td>
    </tr>
    <tr>
      <td>200/5</td>
      <td>16</td>
      <td>16</td>
      <td>828 836,00</td>
      <td>828 827,00</td>
      <td>49,00</td>
      <td>49,00</td>
    </tr>
    <tr>
      <td>200/5</td>
      <td>20</td>
      <td>16</td>
      <td>810 555,00</td>
      <td>810 553,00</td>
      <td>77,00</td>
      <td>77,00</td>
    </tr>
  </tbody>
</table>

# RedPanda

<table>
  <tbody>
    <tr>
      <td><em>Version</em></td>
      <td>21.7.4</td>
    </tr>
    <tr>
      <td><em>Replication</em></td>
      <td>configurable, asynchronous &amp; synchronous</td>
    </tr>
    <tr>
      <td><em>Replication type</em></td>
      <td>active-active</td>
    </tr>
  </tbody>
</table>

The [RedPanda](https://vectorized.io) system targets mission-critical workloads, and exposes a Kafka-compatible API. Hence, the way messaging works in RedPanda carries over from Kafka - we've got topics, partitions, consumer groups etc. In fact, we're using exactly the same client code to test both RedPanda and Kafka. However, the devil lies in the details! 

Let's start with data safety. RedPanda's motto, "Zero data loss", indicates its focus on mission-critical systems. By default, RedPanda's configuration for a 3-node cluster [corresponds](https://vectorized.io/blog/kafka-redpanda-availability/) to the following Kafka properties: 

* `acks=-1`
* `min.insync.replicas=2` (quorum)
* `log.flush.interval.messages=1`

The last one is especially interesting, as that's where RedPanda differs from what you'd often use in a synchronously-replicated Kafka setup, and also from what we've used in our tests. In Kafka, setting `log.flush.interval.messages` to `1` ensures that the disk cache is flushed on every message, and that's what happens in RedPanda as well. In other words, once a message is accepted by the quorum, it is guaranteed that it will be persistently stored on disk (the default in Kafka, and in our tests, is an unbounded number of messages, hence disk flushes happen asynchronously). This approach to disk safety is similar to what we've seen in RabbitMQ. Keep this in mind while reading the results.

On the inside, RedPanda uses a mixture of C++ and Go, while Kafka is JVM-based. Moreover, one of the main selling points of RedPanda is that it eliminates the dependency on ZooKeeper. Instead, it uses the Raft consensus protocol. This has very practical consequences: RedPanda will accept a write once a majority of nodes (the quorum) accepts it; Kafka, on the other hand, will wait for a confirmation from all in-sync-replicas, which might take a longer time (if the ISR set is larger than the quorum). This also means that any disturbance in the cluster will have larger implications on latencies in Kafka, than in RedPanda. It's worth noting that Kafka goes in the same direction with its [KRaft](https://www.confluent.io/blog/kafka-without-zookeeper-a-sneak-peek/) implementation.

RedPanda comes with other interesting features, such as an [auto-tuner](https://vectorized.io/docs/autotune/), which detects the optimal settings given the hardware it's running on. Or the [Wasm transformations](https://vectorized.io/docs/guide-wasm-elastic) supports: think of it as an in-process Kafka streams stage. Another interesting aspect is that RedPanda exposes [Prometheus metrics](https://vectorized.io/docs/monitoring/) natively.

Let's take a look at the performance results. Our test environment goes against RedPanda's guidelines not to use networked block devices (we're using EBS's `gp2` drives), however we wanted to keep the test environment the same for all queues.

RedPanda achieved up to about **15 300 msgs/s** using 200 partitions, 8 sender nodes, 8 receiver nodes each running 25 threads:

<a href="https://snapshot.raintank.io/dashboard/snapshot/OYGvKERHlE8ToC5lPmfyQeTen2CElzR0" target="_blank">
    ![RedPanda grafana](redpanda%20grafana.png)
</a>

Again, that's quite similar to what RabbitMQ achieves. Maybe that's the limit of queues which fsync each received message (or in our test scenario - a batch of up to 10 messages)? How does Kafka behave when we set `log.flush.interval.messages=1`? Turns out, it's a bit faster. We've managed to get to **20 800 msgs/s** using 64 partitions, 8 sender nodes (running 200 threads each) and 8 receiver nodes (running 5 threads each). However, the latencies went up to about 800ms:

<a href="https://snapshot.raintank.io/dashboard/snapshot/aIl4vxmc6Vs0xCg0RQPYwvR9REJNMb3R" target="_blank">
    ![Kafka flush grafana](kafka%20flush%20grafana.png)
</a>

Finally, here are RedPanda's test results in full. Similarly to Kafka, both send and processing latencies oscillate around 47ms, though they do get slightly higher as we increase the number of senders to get the most performance:

<table>
  <thead>
    <tr>
      <th>Threads</th>
      <th>Sender nodes</th>
      <th>Receiver nodes</th>
      <th>Send msgs/s</th>
      <th>Receive msgs/s</th>
      <th>Processing latency</th>
      <th>Send latency</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>25</td>
      <td>1</td>
      <td>2</td>
      <td>4 889</td>
      <td>4 846</td>
      <td>47</td>
      <td>47</td>
    </tr>
    <tr>
      <td>25</td>
      <td>1</td>
      <td>1</td>
      <td>8 057</td>
      <td>8 057</td>
      <td>48</td>
      <td>48</td>
    </tr>
    <tr>
      <td>25</td>
      <td>2</td>
      <td>4</td>
      <td>14 453</td>
      <td>14 454</td>
      <td>48</td>
      <td>48</td>
    </tr>
    <tr>
      <td>25</td>
      <td>2</td>
      <td>4</td>
      <td>14 638</td>
      <td>14 637</td>
      <td>48</td>
      <td>48</td>
    </tr>
    <tr>
      <td>5</td>
      <td>8</td>
      <td>8</td>
      <td>14 730</td>
      <td>14 738</td>
      <td>47</td>
      <td>47</td>
    </tr>
    <tr>
      <td>25</td>
      <td>8</td>
      <td>8</td>
      <td>15 369</td>
      <td>15 369</td>
      <td>141</td>
      <td>137</td>
    </tr>
  </tbody>
</table>

# Summary of features

Below you can find a [summary of some of the characteristics](https://docs.google.com/spreadsheets/d/1DrklYQsYYbmegaj5j6Owu_3h7A-4xwNXanjwKXoaz8o) of the queues that we’ve tested. Of course this list isn’t comprehensive, rather it touches on areas that we’ve mentioned above, and which are important when considering replication, message persistence and data safety. However, each system has a number of unique features, which are out of scope here.

<a href="https://docs.google.com/spreadsheets/d/1DrklYQsYYbmegaj5j6Owu_3h7A-4xwNXanjwKXoaz8o" target="_blank">
    ![Summary of features](summary.png)
</a>

# Which queue to choose?

It depends! Unfortunately, there are no easy answers to such a question :).

As always, which message queue to choose depends on specific project requirements. All of the above solutions have some good sides:

* SQS is an as-a-service offering, so especially if you are using the AWS cloud, it's an easy choice: good performance and no setup required. It's cheap for low to moderate workloads, but might get expensive with high load
* if you are already using Mongo, PostgreSQL or EventStore, you can either use it as a message queue or easily build a message queue on top of the database, without the need to create and maintain a separate messaging cluster
* if you want to have high persistence guarantees, RabbitMQ ensures replication across the cluster and on disk on message send. It's a very popular choice used in many projects, with full AMQP implementation and support for many messaging topologies
* ActiveMQ Artemis is a popular, battle-tested and widely used messaging broker with wide protocol support and good performance
* NATS Streaming support many useful communication patterns, and is especially popular in IoT deployments  
* Redis Streams offers good performance on top of a popular and familiar key-value store
* RocketMQ offers a JMS-compatible interface, with great performance
* Pulsar builds provides a wide feature set, with many messaging schemes available. It’s gaining popularity, due to it’s flexible nature, accommodating for a wide range of use-cases, and great performance
* Kafka offers the best performance and scalability, at the cost of feature set. It is the de-facto standard for processing event streams across enterprises.
* RedPanda exposes a Kafka-compatible interface, focusing on zero data loss, and providing additional data processing and observability features

Here’s a summary of the performance tests. First, zooming in on our database-based queues, Rabbit, NATS Streaming and Artemis, with SQS for comparison:

![Summary mqs](summary1.png)

And including all of the tested queues:

![Summary throughput](summary2.png)

Finally, the processing latency has a wider distribution across the brokers. Usually, it's below 150ms - with RocketMQ, PostgreSQL and KMQ faring worse under high load:

![Summary processing latency](summary_latencies.png)

There are of course many other aspects besides performance, which should be taken into account when choosing a message queue, such as administration overhead, network partition tolerance, feature set regarding routing, documentation quality, maturity etc. While there's no message-queueing silver bullet, hopefully this summary will be useful when choosing the best system for your project!

# Credits

The following team members contributed to this work: [Grzegorz Kocur](https://twitter.com/GrzegorzKocur), [Maciej Opała](https://twitter.com/czaszo), [Marcin Kubala](https://twitter.com/marcin_kubala), [Krzysztof Ciesielski](https://twitter.com/kpciesielski), [Kasper Kondzielski](https://twitter.com/kkondzielski), Tomasz Król, [Adam Warski](https://twitter.com/adamwarski). [Clebert Suconic](https://twitter.com/clebertsuconic), [Michael André Pearce](https://twitter.com/itsourcery), [Greg Young](https://twitter.com/gregyoung) and [Francesco Nigro](https://github.com/franz1981) helped out with some configuration aspects of Artemis and EventStore. Thanks!

