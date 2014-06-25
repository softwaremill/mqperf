# Docker-Spray example
# VERSION 1.0

# the base image is a trusted ubuntu build with java 7 (https://index.docker.io/u/dockerfile/java/)
FROM dockerfile/java

# that's me!
MAINTAINER Adam Warski, adam@warski.org

# run the (java) server as the daemon user
USER daemon

# copy the locally built fat-jar to the image
ADD target/mqperf-assembly-1.0-deps.jar /app/deps.jar
ADD target/mqperf-assembly-1.0.jar /app/app.jar

# run the server when a container based on this image is being run
ENTRYPOINT [ "java", "-cp", "/app/deps.jar:/app/app.jar" ]
