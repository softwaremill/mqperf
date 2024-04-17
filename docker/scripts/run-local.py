import json
import sys
import time
from datetime import datetime, timedelta, timezone
from pprint import pprint

import requests

from grafana import save_snapshot
from prometheus import avg_receive_metric, avg_send_metric, send_latency_metric, receive_latency_metric

base_url = 'http://localhost:8080'
prometheus_url = 'http://localhost:9090'

grafana_url = 'http://admin:admin@localhost:3000'
grafana_mqperf_dashboard_id = 2


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

    start = datetime.now(timezone.utc)

    while True:
        in_progress = requests.get(base_url + '/in-progress').json()
        if not in_progress:
            break
        else:
            print('Still running:', in_progress)
            time.sleep(1)

    end = datetime.now(timezone.utc)

    print('Test time range:')
    print(start.isoformat())
    print(end.isoformat())

    requests.post(base_url + '/cleanup', json=payload)
    print('Cleanup complete')

    expire = None
    if payload['grafana']['snapshot']['expire']:
        expire = payload['grafana']['snapshot']['expire']

    if payload['grafana']['snapshot']['delayStartSec']:
        start = start + timedelta(seconds=payload['grafana']['snapshot']['delayStartSec'])

    if payload['grafana']['snapshot']['trimEndSec']:
        end = end - timedelta(seconds=payload['grafana']['snapshot']['trimEndSec'])

    print("Avg of total receive rate")
    pprint(avg_receive_metric(prometheus_url, start, end))

    print("Avg of total sent rate")
    pprint(avg_send_metric(prometheus_url, start, end))

    print("Max 95p of receive latency")
    pprint(receive_latency_metric(prometheus_url, start, end))

    print("Max 95p of send latency")
    pprint(send_latency_metric(prometheus_url, start, end))

    snapshot_link = save_snapshot(grafana_url, grafana_mqperf_dashboard_id, start, end, expire)

    print('Link to saved snapshot')
    print(snapshot_link['url'])

    print('Link to delete the saved snapshot')
    print(snapshot_link['deleteUrl'])


def check_if_ok(resp):
    if not resp.ok:
        raise Exception(f'Request failed: {resp.status_code} - {resp.reason}')
    return resp


if __name__ == '__main__':
    test_file = sys.argv[1]
    run(test_file)
