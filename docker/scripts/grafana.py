from datetime import datetime

import requests

query_format = '%Y-%m-%dT%H:%M:%S.%fZ'


def fetch_dashboard(grafana_url, grafana_mqperf_dashboard_id):
    # https://grafana.com/docs/grafana/v9.3/developers/http_api/dashboard/#get-dashboard-by-uid
    print(f'Getting the full dashboard with id: {grafana_mqperf_dashboard_id}')
    dashboard_json = requests.get(f'{grafana_url}/api/dashboards/uid/{grafana_mqperf_dashboard_id}').json()
    dashboard = dashboard_json['dashboard']

    dashboard['fiscalYearStartMonth'] = 0
    dashboard['annotations']['list'][0]['snapshotData'] = []
    dashboard['liveNow'] = False
    dashboard['refresh'] = False
    dashboard['snapshot'] = {
        'timestamp': f'{datetime.utcnow()}'
    }
    dashboard['weekStart'] = ''

    del dashboard['annotations']['list'][0]['datasource']

    print(f"Got dashboard: {dashboard['title']}")
    return dashboard


def prepare_query(start, end, expr, format_name, interval_factor, metric, ref_id, step, datasource):
    return {
        "queries": [
            {
                "expr": f"{expr}",
                "format": f"{format_name}",
                "intervalFactor": interval_factor,
                "legendFormat": "",
                "metric": f"{metric}",
                "refId": f"{ref_id}",
                "step": step,
                "datasource": datasource,
                "queryType": "timeSeriesQuery",
                "intervalMs": 1000,
                "maxDataPoints": 100
            }
        ],
        "range": {
            "from": f"{start.strftime(query_format)}",
            "to": f"{end.strftime(query_format)}",
            "raw": {
                "from": f"{start.strftime(query_format)}",
                "to": f"{end.strftime(query_format)}",
            }
        },
        "from": f"{int(round(start.timestamp() * 1000))}",
        "to": f"{int(round(end.timestamp() * 1000))}"
    }


def query_result_to_snapshot_data(query_result):
    data = {
        'fields': [],
        'meta': {},
        'name': '',
        'refId': ''
    }

    for key in query_result['results']:
        for index in range(len(query_result['results'][key]['frames'])):
            result = query_result['results'][key]['frames'][index]
            meta = result['schema']['meta']
            meta['preferredVisualisationType'] = 'graph'
            data = {
                'fields': result['schema']['fields'],
                'meta': meta,
                'name': result['schema']['name'],
                'refId': result['schema']['refId']
            }

            for vIndex in range(len(result['data']['values'])):
                if 'typeInfo' in data['fields'][vIndex]:
                    del data['fields'][vIndex]['typeInfo']
                data['fields'][vIndex]['values'] = result['data']['values'][vIndex]

    return data


def fetch_panel_data(grafana_url, panel, start, end):
    print(f"Processing panel: {panel['id']}")

    panel['snapshotData'] = []

    for index in range(len(panel['targets'])):
        expr = panel['targets'][index]['expr'].replace('$span', '1m')
        format_name = panel['targets'][index]['format']
        interval_factor = panel['targets'][index]['intervalFactor']
        metric = panel['targets'][index].get('metric')
        ref_id = panel['targets'][index]['refId']
        step = panel['targets'][index]['step']

        query = prepare_query(start, end, expr, format_name, interval_factor, metric, ref_id, step, panel['datasource'])

        # https://grafana.com/docs/grafana/v9.3/developers/http_api/data_source/#query-a-data-source
        result = requests.post(f'{grafana_url}/api/ds/query', json=query).json()

        snapshot_data = query_result_to_snapshot_data(result)

        panel['snapshotData'].append(snapshot_data)

    panel['targets'] = []
    panel['datasource'] = None

    return panel


def save_snapshot(grafana_url, grafana_mqperf_dashboard_id, start, end, expire):
    # read dashboard
    dashboard = fetch_dashboard(grafana_url, grafana_mqperf_dashboard_id)

    panels = []
    for panel in dashboard['panels']:
        panels.append(fetch_panel_data(grafana_url, panel, start, end))

    dashboard['panels'] = panels
    dashboard['timezone'] = 'utc'
    dashboard['time']['from'] = start.strftime(query_format)
    dashboard['time']['to'] = end.strftime(query_format)

    # print(json.dumps(dashboard))

    title = 'MQPerf Dashboard - Kafka - Snapshot'
    dashboard['title'] = title
    snapshot_payload = {
        'dashboard': dashboard,
        'name': title,
        'external': True
    }

    if expire is not None:
        snapshot_payload['expires'] = expire

    # https://grafana.com/docs/grafana/v9.3/developers/http_api/snapshot/#create-new-snapshot
    return requests.post(f'{grafana_url}/api/snapshots', json=snapshot_payload).json()
