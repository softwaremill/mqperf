import requests
import json
import time
import sys
from kubernetes import client, config
from kubernetes.stream import portforward
import portforward
import _portforward
from pathlib import Path
from datetime import datetime, timezone


def run(test_file: str):
    with open(test_file) as f:
        payload = json.load(f)

    config.load_kube_config()
    v1 = client.CoreV1Api()
    namespace = 'default'
    app_pods = [pod for pod in v1.list_namespaced_pod(namespace, watch=False).items if pod.metadata.name.startswith('app-deployment')]

    print("MQPerf app pods:")
    for pod in app_pods:
        print(pod.metadata.name)

    instances = []
    port = 8080
    config_path = str(Path.home() / ".kube" / "config")
    for idx, pod in enumerate(app_pods):
        local_port = port + idx
        _portforward.forward(namespace, pod.metadata.name, local_port, port, config_path, portforward.LogLevel.DEBUG.value)
        instances.append((pod, local_port, f"http://localhost:{local_port}"))
    time.sleep(5) # sleep to ensure port forwarding is operational

    _, _, base_url = instances[0]

    requests.post(base_url + '/cleanup', json=payload)
    print('Cleanup complete')

    check_if_ok(requests.post(base_url + '/init', json=payload))
    print('Initialized')

    for (pod, _, url), job in zip(instances, payload['jobs']):
        check_if_ok(requests.post(url + '/start/' + job, json=payload))
        print(f'Job {job} started on {pod.metadata.name}')

    start = datetime.now(timezone.utc)

    while True:
        jobs_in_progress = []
        for _, _, url in instances:
            jobs_in_progress.extend(requests.get(url + '/in-progress').json())
        if not jobs_in_progress:
            break
        else:
            print('Still running:', jobs_in_progress)
            time.sleep(10)

    end = datetime.now(timezone.utc)

    print('Test time range:')
    print(start.isoformat())
    print(end.isoformat())

    requests.post(base_url + '/cleanup', json=payload)
    print('Cleanup complete')

    # TODO: use docker/scripts/grafana.py
    #       use docker/scripts/prometheus.py
    #       see docker/scripts/run-local.py how to use them

    # TODO: save metrics in DB, the url to DB with credentials must be provided in the JSON config
    #       this DB must accessible over the internet, DevOps will create a dedicate standalone instance


def check_if_ok(resp):
    if not resp.ok:
        raise Exception('Request failed: ' + resp)
    return resp


if __name__ == '__main__':
    run(sys.argv[1])
