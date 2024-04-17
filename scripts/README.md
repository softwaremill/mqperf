# Running test

To run a test of a given queue system you must first prepare a cluster, see [readme](../README.md) how to set up the cluster first.

Having cluster _up & running_ you can use `run_test.py` to perform the test. To run the test you must provide a config for the given test run. See the [example](test-example-kafka.json) JSON to understand what should be defined.

TODO: describe all available options in the JSON

To run the test use the following syntax:

```python
python3 run_test.py <mq-config>.json
```

# TODO
 - see `run_test.py` for additional TODOs
 - prepare a _test runner_ script which can be run standalone on python pod,
   see [this](https://softwaremill.slack.com/archives/C3EDBMX0R/p1672656117127199) discussion for more details
 - use DB to store metrics per test run
 - use metrics to adjust test
   - after each run compare `Max 95p of receive latency` with `Max 95p of send latency`
   - if the difference is in 5%, 10% (choose a number)
   - increase numer of messages, or increase number od senders&receivers to perform another test
   - and store metrics for that new test
   - if the difference is to high, stop the test
