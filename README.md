mqperf
======

Client jar deployment

* in the sbt build, setup assembly plugin to create app and dep jars

* create a dockerfile which creates an image with the jars, and a script to run it
using jars only if they have been modified

* push the image to docker's registry (docker push adamw/mqperf)

Remote:

* setup an opsworks stack using the Ubuntu 14.04 AMI. All config should go into the custom stack JSON

* setup an opsworks layer for the client, using the custom recipes

* create a dummy app to be able to trigger deployment

* after each change, run the "update custom cookbooks" and deploy the app

Local:

* create a vagrantfile based on https://github.com/wwestenbrink/vagrant-opsworks

* use the same recipes as in opsworks

Oracle AQ support:

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