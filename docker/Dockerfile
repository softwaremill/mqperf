# the base image is https://registry.hub.docker.com/_/java/
FROM java:8

# that's me!
MAINTAINER Adam Warski, adam@warski.org

# copy the locally built fat-jar to the image
ADD target/mqperf-assembly-1.0-deps.jar /app/deps.jar
ADD target/mqperf-assembly-1.0.jar /app/app.jar

# run the server when a container based on this image is being run
ENTRYPOINT [ "java", "-cp", "/app/deps.jar:/app/app.jar" ]
