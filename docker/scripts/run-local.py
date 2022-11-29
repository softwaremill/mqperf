import requests
import json
import time
import sys
from datetime import datetime, timedelta
from pprint import pprint

base_url = 'http://localhost:8080'
prometheus_url = 'http://localhost:9090'

METRICS_QUERY_STEP_SEC = 1
SEND_LATENCY_METRIC = 'histogram_quantile(0.95, sum(rate(mqperf_send_latency_ms_bucket[1m])) by (le))'
RECEIVE_LATENCY_METRIC = 'histogram_quantile(0.95, sum(rate(mqperf_receive_latency_ms_bucket[1m])) by (le))'
QUERY_DELAY_SEC = 60

def run(test_file: str):
    with open(test_file) as f:
        payload = json.load(f)

    requests.post(base_url + '/cleanup', json=payload)
    print('Cleanup complete')

    check_if_ok(requests.post(base_url + '/init', json=payload))
    print('Initialized')

    check_if_ok(requests.post(base_url + '/start/sender', json=payload))
    print('Started sender')

    check_if_ok(requests.post(base_url + '/start/receiver', json=payload))
    print('Started receiver')

    start = datetime.utcnow()

    while True:
        in_progress = requests.get(base_url + '/in-progress').json()
        if not in_progress:
            break
        else:
            print('Still running:', in_progress)
            time.sleep(1)

    end = datetime.utcnow()

    print('Test time range:')
    print(start.isoformat())
    print(end.isoformat())

    requests.post(base_url + '/cleanup', json=payload)
    print('Cleanup complete')

    print("Send latency histogram:")
    pprint(query_metrics(start, end, SEND_LATENCY_METRIC))

    print("Receive latency histogram:")
    pprint(query_metrics(start, end, RECEIVE_LATENCY_METRIC))

def check_if_ok(resp):
    if not resp.ok:
        raise Exception('Request failed: ' + resp)
    return resp

def query_metrics(start: datetime, end: datetime, metric_query: str):
    td = timedelta(seconds=QUERY_DELAY_SEC)
    start = start + td
    end = end - td
    metrics_data = requests.get(f'{prometheus_url}/api/v1/query_range', {
        'start': start.isoformat() + 'Z',
        'end': end.isoformat() + 'Z',
        'step': METRICS_QUERY_STEP_SEC,
        'query': metric_query
    }).json()

    return [e[1] for e in metrics_data['data']['result'][0]['values']]

if __name__ == '__main__':
    test_file = sys.argv[1]
    run(test_file)
