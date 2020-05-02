/* eslint-disable import/no-extraneous-dependencies */
const cryptoRandomString = require('crypto-random-string');
const randomNormal = require('random-normal');
const db = require('./db');

function CoordinateTransforms(center) {
  const EARTH_R = 6.371e6;
  const METERS_PER_LAT = Math.PI * EARTH_R / 180.0;
  const metersPerLon = METERS_PER_LAT * Math.cos(center.latitude / 180.0 * Math.PI);

  this.wgs2enu = (latitude, longitude) => ({
    x: (longitude - center.longitude) * metersPerLon,
    y: (latitude - center.latitude) * METERS_PER_LAT
  });

  this.enu2wgs = (x, y) => ({
    latitude: y / METERS_PER_LAT + center.latitude,
    longitude: x / metersPerLon + center.longitude
  });

  const wgs2enuObj = ({ latitude, longitude }) => this.wgs2enu(latitude, longitude);

  this.distance = (wgs0, wgs1) => {
    const enu0 = wgs2enuObj(wgs0);
    const enu1 = wgs2enuObj(wgs1);
    return Math.sqrt((enu1.x - enu0.x) ** 2 + (enu1.y - enu0.y) ** 2);
  };
}

const CENTER = {
  // Helsinki, Finland
  latitude: 60.1628855,
  longitude: 24.94375
};
const SCALE_METERS = 1500;

function linspace(min, max, num) {
  const a = [];
  for (let i = 0; i < num; ++i) {
    a.push(min + (max - min) / (num - 1) * i);
  }
  return a;
}

function randomWalk(startXY, scaleMeters) {
  let { x, y } = startXY;

  scaleMeters = SCALE_METERS * 0.2;
  const SIGMA_V = scaleMeters / 100;
  const V_DAMP = 0.03;
  const WALK_LENGTH = 200;

  let vx = randomNormal({ dev: 10 * SIGMA_V });
  let vy = randomNormal({ dev: 10 * SIGMA_V });

  const walk = [];
  for (let i = 0; i < WALK_LENGTH; ++i) {
    vx = randomNormal({ mean: vx, dev: SIGMA_V }) * (1 - V_DAMP);
    vy = randomNormal({ mean: vy, dev: SIGMA_V }) * (1 - V_DAMP);
    x += vx;
    y += vy;
    walk.push({ x, y });
  }
  return walk;
}

const coords = new CoordinateTransforms(CENTER);
function randomId() {
  return cryptoRandomString({ length: 10 });
}

function generateAgents() {
  const agents = [];
  const s = SCALE_METERS * 0.5;
  const N_PER_ROW = 20;
  const RANGE_M = 30;

  linspace(-s, s, N_PER_ROW).forEach((x) => {
    linspace(-s, s, N_PER_ROW).forEach((y) => {
      agents.push({
        id: randomId(),
        location: coords.enu2wgs(x, y),
        range: RANGE_M
      });
    });
  });
  return agents;
}

function generateReports(agents) {
  const N_WALKS = 300;
  const RESOLVE_PROB = 0.05;

  const contacts = [];
  const resolvedMap = {};
  let totalHits = 0;
  let totalBroadcasts = 0;

  for (let i = 0; i < N_WALKS; ++i) {
    const resolved = Math.random() < RESOLVE_PROB;
    const resolvedId = resolved && randomId();
    if (resolved) {
      resolvedMap[resolvedId] = [];
    }
    const walk = randomWalk({
      x: randomNormal({ dev: SCALE_METERS }),
      y: randomNormal({ dev: SCALE_METERS })
    });

    let nHits = 0;
    walk.forEach(({ x, y }) => {
      const loc = coords.enu2wgs(x, y);
      const id = randomId();
      if (resolved) resolvedMap[resolvedId].push(id);
      let anyHits = false;
      agents
        .filter((agent) => coords.distance(loc, agent.location) < agent.range)
        .forEach((agent) => {
          anyHits = true;
          contacts.push({
            id,
            agent
          });
        });
      if (anyHits) nHits++;
    });
    totalHits += nHits;
    totalBroadcasts += walk.length;
  }

  const coverRate = Math.round(totalHits / totalBroadcasts * 100);
  console.log(`simulated ${totalBroadcasts} broadcasts and generated `
    + `${contacts.length} contact(s). Cover rate ${coverRate}%`);

  return { contacts, resolved: resolvedMap };
}

async function generateDb() {
  const { contacts, resolved } = generateReports(generateAgents());
  await db.clearAll();

  await Promise.all(contacts.map((contact) => db.insert({
    rollingId: contact.id,
    contactJson: {},
    agentId: contact.agent.id,
    agentJson: contact.agent
  })));
  console.log('inserted contacts');

  await Promise.all(
    Object.entries(resolved)
      .map(([resolvedId, rollingIds]) => db.updateResolved({ resolvedId, rollingIds }))
  );
  console.log('done');
}

generateDb();
