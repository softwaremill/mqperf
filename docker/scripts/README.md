# Test locally

To test locally using Docker compose perform the below steps:

1. Build a docker image with a given client:
  `sbt kafka/docker:publishLocal`
2. Prepare test settings, see `test-kafka.json`
3. Start docker `docker-compose up` and wait a bit before starting the test (ie. it takes a few minutes to start Kafka)
4. Run the test `python run-local.py test-kafka.json` and wait till it finished
5. tbc
