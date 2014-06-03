#!/usr/bin/env bash

sudo su  
echo deb https://get.docker.io/ubuntu docker main > /etc/apt/sources.list.d/docker.list  

apt-key adv --keyserver keyserver.ubuntu.com --recv-keys 36A1D7869245C8950F966E92D8576A8BA88D21E9  
apt-get update 
