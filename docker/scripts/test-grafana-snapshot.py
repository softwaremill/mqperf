from pprint import pprint
from dateutil import parser
from grafana import save_snapshot

grafana_url = 'http://admin:admin@localhost:3000'
grafana_mqperf_dashboard_id = 2

start_str = '2022-12-14 13:18:30'
end_str = '2022-12-14 13:23:59'

# start = datetime.now() - timedelta(minutes=5)
# end = start + timedelta(minutes=10)

start = parser.parse(start_str)
end = parser.parse(end_str)

pprint(start.isoformat())
pprint(end.isoformat())

snapshot_link = save_snapshot(grafana_url, grafana_mqperf_dashboard_id, start, end)
pprint(snapshot_link)
