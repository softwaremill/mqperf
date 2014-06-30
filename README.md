mqperf
======

Client jar deployment

* create a docker-enabled AMI based on Ubuntu 12.04 AMI (ami-ad4f82da,
http://cloud-images.ubuntu.com/releases/precise/release/)

* follow http://www.shopigniter.com/blog/2014/04/01/deploying-docker-with-opsworks/
to setup docker+opsworks

* in the sbt build, setup assembly plugin to create app and dep jars

* create a dockerfile which creates an image with the jars, and a script to run it
using jars only if they have been modified

* push the image to docker's registry (docker push adamw/mqperf)

Remote:

* setup an opsworks stack using the custom AMI. All config should go into the custom JSON

* setup an opsworks layer for the client, using the custom recipes

* create a dummy app to be able to trigger deployment

* after each change, run the "update custom cookbooks" and deploy the app

Local:

* create a vagrantfile based on http://brunobuccolo.com/vagrant-chef-and-opsworks/

* use the same recipes as in opsworks