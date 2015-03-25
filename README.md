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

* create a vagrantfile based on http://brunobuccolo.com/vagrant-chef-and-opsworks/

* use the same recipes as in opsworks