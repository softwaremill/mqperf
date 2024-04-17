from datetime import datetime
import requests
# import json

METRICS_QUERY_STEP_SEC = 1
QUERY_DELAY_SEC = 60

# - Avg of total receive rate
AVG_OF_TOTAL_RECEIVE_RATE_METRIC = "sum(rate(mqperf_received_total[1m]))"
# - Avg of total sent rate
AVG_OF_TOTAL_SENT_RATE_METRIC = "sum(rate(mqperf_sent_total[1m]))"
# - Max 95p of receive latency
MAX_95P_OF_RECEIVE_LATENCY_METRIC = "histogram_quantile(0.95, sum(rate(mqperf_receive_latency_ms_bucket[1m])) by (le))"
# - Max 95p of send latency
MAX_95P_OF_SEND_LATENCY_METRIC = 'histogram_quantile(0.95, sum(rate(mqperf_send_latency_ms_bucket[1m])) by (le))'


def query_metrics(prometheus_url: str, start: datetime, end: datetime, metric_query: str):
    metrics_data = requests.get(f'{prometheus_url}/api/v1/query_range', {
        'start': start.timestamp(),
        'end': end.timestamp(),
        'step': METRICS_QUERY_STEP_SEC,
        'query': metric_query
    }).json()

    # print(json.dumps(metrics_data))

    if metrics_data['status'] == 'error':
        raise Exception(f"Got error trying to fetch metric: '{metric_query}'", metrics_data['error'])
    else:
        return [e[1] for e in metrics_data['data']['result'][0]['values']]


def avg_receive_metric(prometheus_url: str, start: datetime, end: datetime):
    return query_metrics(prometheus_url, start, end, AVG_OF_TOTAL_RECEIVE_RATE_METRIC)


def avg_send_metric(prometheus_url: str, start: datetime, end: datetime):
    return query_metrics(prometheus_url, start, end, AVG_OF_TOTAL_SENT_RATE_METRIC)


def send_latency_metric(prometheus_url: str, start: datetime, end: datetime):
    return query_metrics(prometheus_url, start, end, MAX_95P_OF_SEND_LATENCY_METRIC)


def receive_latency_metric(prometheus_url: str, start: datetime, end: datetime):
    return query_metrics(prometheus_url, start, end, MAX_95P_OF_RECEIVE_LATENCY_METRIC)
