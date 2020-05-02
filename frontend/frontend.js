'use strict';

function initializeMap(bounds) {
  const map = L.map('map').fitBounds(bounds);
  const mapLink = '<a href="http://openstreetmap.org">OpenStreetMap</a>';
  L.tileLayer(
      'http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; ' + mapLink + ' Contributors',
      maxZoom: 19,
      }).addTo(map);
  return map;
}

const fetchJson = (url) => fetch(url).then(response => response.json());

function buildAgentClusters(data) {
  const agentClusters = {};
  let maxTotal = 1;
  data.filter(d => d.json && d.json.location).forEach(d => {
    const id = d.agent_id;
    if (!agentClusters[id])
      agentClusters[id] = {
        location: d.json.location,
        nResolved: 0,
        nTotal: 0
      };
    const c = agentClusters[id];
    c.nTotal++;
    if (d.resolved_id) {
      c.nResolved++;
    }
    maxTotal = Math.max(c.nTotal, maxTotal);
  });
  function valueToRadius(x) {
    const MAX_R_PIXELS = 50;
    return Math.sqrt(x/maxTotal)*MAX_R_PIXELS;
  }
  const markers = L.featureGroup();
  [false, true].forEach(resolvedOnly => {
    Object.values(agentClusters).map((cluster) => {
      const loc = [cluster.location.latitude, cluster.location.longitude];
      const n = resolvedOnly ? cluster.nResolved : cluster.nTotal;
      if (n > 0) {
        L.circleMarker(loc, {
            radius: valueToRadius(n),
            fillColor: resolvedOnly ? 'red' : 'blue',
            fillOpacity: resolvedOnly ? 0.8 : 0.1,
            stroke: false
          })
          .addTo(markers);
      }
    });
  });
  return markers;
}

function buildResolvedPaths(data) {
  const paths = {};
  let maxTotal = 1;
  data.filter(d => d.json && d.json.location).forEach(d => {
    const id = d.resolved_id;
    if (!paths[id]) paths[id] = [];
    paths[id].push({
      location: d.json.location,
      time: d.time,
      agentId: d.agent_id
    });
  });
  function strComparator(field) {
    return (a, b) => (a[field] > b[field]) - (a[field] < b[field]);
  }
  const lines = L.featureGroup();
  let lineIdx = 0;
  const nLines = Object.keys(paths).length;
  Object.entries(paths).map(([id, points]) => {
    points.sort(strComparator('time'));
    let prevAgentId = null;
    const deduplicated = [];
    points.forEach(p => {
      if (p.agentId !== prevAgentId) deduplicated.push(p);
      prevAgentId = p.agentId;
    });
    // small offset so it's possible to distinguish overlapping edges
    // when zooming in
    const offset = 0.00002 * lineIdx / nLines;
    const coords = points.map(p => [
      p.location.latitude + offset,
      p.location.longitude + offset*0.5
    ]);
    const hue = Math.round(lineIdx / nLines * 360);
    L.polyline(coords, {
      color: `hsl(${hue}, 100%, 80%)`
    }).addTo(lines);
    lineIdx++;
  });
  return lines;
}

Promise.all([
  fetchJson('/all'),
  fetchJson('/resolved')
]).then(([agentData, resolvedData]) => {
  const agentClusters = buildAgentClusters(agentData);
  const map = initializeMap(agentClusters.getBounds());
  agentClusters.addTo(map);
  buildResolvedPaths(resolvedData).addTo(map);
});
