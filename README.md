# MqPerf

A benchmark of message queues with data replication and at-least-once delivery guarantees.

# Setting up the environment

Message queues and test servers are automatically provisioned using Ansible on AWS. You will need to have the
`AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` present in the environment for things to work properly.

Metrics are gathered using Prometheus and visualized using Grafana.

Here are the steps needed to test Kafka (other queues are similar). Open the `ansible` directory in the console and:

* provision a kafka cluster by running `ansible-playbook install_and_setup_kafka.yml`. Note to change the size of the
instance to the desired one.
* provision a number of sender and receiver nodes using `ansible-playbook provision_mqperf_nodes.yml`. Adjust the
number and size of nodes depending on the test you want to run. Keep in mind that after each code change, you'll need
to remove the fat-jars from the `target/scala-2.11` directory and re-run `provision_mqperf_nodes.yml`.
* provision the prometheus/grafana server by running `ansible-playbook install_and_setup_prometheus.yml`. This must be
done each time after provisioning new sender/receiver nodes (previous step) so that prometheus is properly configured
to scrape the new servers for metrics
* setup grafana: open the grafana panel on the `:3000` port (`admin`/`pass`), create a new prometheus data source 
(`local-instance-ip:3000`), and import the dashboard from json (`prometheus/dashboard.json`)
* modify `run-tests.yml` with the correct test name, run the test, observe results!

# Implementation-specific notes

## Kafka

Before running the tests, create the kafka topics by running `ansible-playbook kafka_create_topic.yml`

## RabbitMQ

* when installing rabbit mq, you need to specify the erlang cookie, e.g.: 
`ansible-playbook install_and_setup_rabbitmq.yml -e erlang_cookie=1234`
* the management console is available on port 15672
* if you'd like to ssh to the broker servers the user is `centos`
* queues starting with `ha.` will be mirrored

# Oracle AQ support

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

# Notes

Zookeeper installation contains an ugly workaround for a bug in Cloudera's RPM repositories (http://community.cloudera.com/t5/Cloudera-Manager-Installation/cloudera-manager-installer-fails-on-centos-7-3-vanilla/td-p/55086/highlight/true).
See `ansible/roles/zookeeper/tasks/main.yml`. This should be removed in the future when the bug is fixed by Cloudera.