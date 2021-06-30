# MqPerf

A benchmark of message queues with data replication and at-least-once delivery guarantees.

This repository is related to the SoftwareMill blog post [Evaluating persistent, replicated message queues](https://softwaremill.com/mqperf)

# Setting up the environment

### Tools
Tests have been run with the following prerequisites:
- python 3.9.5 (`via pyenv`)
- ansible 2.9.5 (`pip install 'ansible==2.9.5'`)
- boto3 1.17.96 (`pip install boto3`)

### AWS Credentials
Message queues and test servers are automatically provisioned using **Ansible** on **AWS**. You will need to have the
`AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` present in the environment for things to work properly, as well
as Ansible and Boto installed.

See [Creating AWS access key](https://aws.amazon.com/premiumsupport/knowledge-center/create-access-key/) for details.

# Message generation notes
* By default, each message has length of 100 characters (this is configurable)
* For each test, we generate a pool of 10000 random messages
* Each batch of messages is constructed using messages from that pool
* Each message in the batch is modified: 13 characters from the end are replaced with stringified timestamp
  (TS value is used for measurement on the receiver end)

Please consider the above when configuring message size parameter in test configuration: ```"msg_size": 100```.
If message is too short, then majority of its content will be the TS information. For that reason, we suggest
configuring message length at 50+ characters.

# Configuring tests
Test configurations are located under `ansible/tests`. Each configuration has a number of parameters 
that may influence the test execution and its results.

# Running tests
*Note: all commands should be run in the `ansible` directory*

### Provision broker nodes with relevant script
```shell
ansible-playbook install_and_setup_YourQueueName.yml
```
*Note: since **AWS SQS** is a serverless offering, you don't need to setup anything for it. For SQS, you can skip this step.*

*Note: you can select EC2 instance type for your tests by setting `ec2_instance_type` in the `group_vars/all.yml` file*
 
### Provision sender and receiver nodes
```shell
ansible-playbook provision_mqperf_nodes.yml
```
*Note: you can adjust the number of these **EC2** instances for your own tests.*

**WARNING: after each code change, you'll need to remove the fat-jars from the `target/scala-2.12` directory and re-run 
`provision_mqperf_nodes.yml`.**

### Provision Prometheus and Grafana nodes
```shell
ansible-playbook install_and_setup_prometheus.yml
```
**WARNING: this must be done each time after provisioning new sender / receiver nodes (previous step) so that Prometheus 
is properly configured to scrape the new servers for metrics**

### Monitoring tests
Metrics are gathered using **Prometheus** and visualized using **Grafana**.

Accessing monitoring dashboard:
* Lookup the *public* IP address of the EC2 node where metric tools have been deployed.
* Open `IP:3000/dashboards` in your browser
* Login with `admin/pass` credentials
* Select `MQPerf Dashboard`

### Execute test
* Choose your test configuration from the `tests` directory
* Use the file name as the `test_name` in the `run_tests.yml` file
* Run the command
```shell
ansible-playbook run_tests.yml
```

# Cleaning up
There are few commands dedicated to cleaning up the cloud resources after the tests execution.

* Stopping sender and receiver processing
```shell
ansible-playbook stop.yml
```

* Terminating EC2 instances
```shell
ansible-playbook shutdown_ec2_instances.yml
```

* Removing all MQPerf-related resources on AWS
```shell
ansible-playbook remove_aws_resources.yml
```

# Utilities
* Checking receiver/sender status
```shell
ansible-playbook check_status.yml
```

* Running sender nodes only
```shell
ansible-playbook sender_only.yml
```

* Running receiver nodes only
```shell
ansible-playbook receiver_only.yml
```

# Implementation-specific notes

## Kafka
Before running the tests, create the Kafka topics by running `ansible-playbook kafka_create_topic.yml`

## Redpanda [vectorized.io]
Redpanda requires xfs filesystem, to configure it update `storage_fs_type: xfs` in `all.yml` file.
Before running the tests, create the Redpanda topics by running `ansible-playbook redpanda_create_topic.yml`.
Default partition number in a topic creation script is 64, if you need to adjust it update `--partitions 64` param in `redpanda_create_topic.yml` script.
## Pulsar
The ack property is set on the Bookkeeper level via the CLI or REST or a startup parameter. 
[Go to the docs](https://pulsar.apache.org/docs/en/administration-zk-bk/#bookkeeper-persistence-policies) for more details.
Currently, this is not implemented, hence the `mq.ack` attribute is ignored.

## RabbitMQ
* when installing Rabbit MQ, you need to specify the Erlang cookie, e.g.: 
`ansible-playbook install_and_setup_rabbitmq.yml -e erlang_cookie=1234`
* management console is available on port 15672 (`guest`/`guest`)     
* if you'd like to SSH to the broker servers the user is `centos`
* queues starting with `ha.` will be mirrored

## ActiveMQ
* management console is available on port 8161 (`admin`/`admin`)

## ActiveMQ Artemis
* note that for the client code, we are using the same one as for ActiveMQ (`ActiveMq.scala`)
* there is no dedicated management console for ActiveMQ Artemis, however monitoring is possible via exposed [Jolokia](https://jolokia.org/) web app. Jolokia web application is deployed along ActiveMQ Artemis by default. To view broker's data:
    * Navigate to: `http://<AWS_EC2_PUBLIC_IP>:8161/jolokia/list` - plain JSON content should be visible - to verify if it works.
    * To view instance's state navigate to e.g.: `http://<AWS_EC2_PUBLIC_IP>:8161/jolokia/read/org.apache.activemq.artemis:address="mq",broker="<BROKER_NAME>",component=addresses`, where: `org.apache.activemq.artemis:address="mq",broker="<BROKER_NAME>",component=addresses` is the key (`"` signs are obligatory). To know other keys refer to the previous step. 
    * `<BROKER_NAME>` typically resolves to AWS_EC2_PRIVATE_IP with `.` replaced with `_`.
* configuration changes: bumped Xmx, bumped global-max-size    
    
## EventStore
* configuration changes: see the `EventStoreMq` implementation

## Oracle AQ support
* to build the oracleaq module, first install the required dependencies available in your Oracle DB installation
    * aqapi.jar (oracle/product/11.2.0/dbhome_1/rdbms/jlib/aqapi.jar)
    * ojdbc6.jar (oracle/product/11.2.0/dbhome_1/jdbc/lib/ojdbc6.jar)

* to install a dependency in your local repository, create a build.sbt file:
```
organization := "com.oracle"
name := "ojdbc6"
version := "1.0.0"
scalaVersion := "2.11.6"
packageBin in Compile := file(s"${name.value}.jar")
```
Now you can publish the file. It should be available in ~/.ivy2/local/com.oracle/
```sh
$ sbt publishLocal
```

# Ansible notes
Zookeeper installation contains an ugly workaround for a bug in Cloudera's RPM repositories 
(http://community.cloudera.com/t5/Cloudera-Manager-Installation/cloudera-manager-installer-fails-on-centos-7-3-vanilla/td-p/55086/highlight/true).
See `ansible/roles/zookeeper/tasks/main.yml`. This should be removed in the future when the bug is fixed by Cloudera.

# FAQ
- I'm getting: *skipping: no hosts matched*, why? Probably you are running ansible from project root.
  Instead `cd` to `ansible/` (where `ansible.cfg` is located) and try to run playbook from this location.

# Local test
To run locally execute the Sender and Receiver classes with following:
- parameters:

`-Dconfig.file=/tmp/test-config.json`

- environment variables:

`RUN_ID=1;HOST_ID=1`
