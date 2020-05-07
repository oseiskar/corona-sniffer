const stream = require('stream');
const Koa = require('koa');
const KoaRouter = require('koa-router');
const bodyParser = require('koa-bodyparser');
const db = require('./db');
const cryptography = require('./cryptography');

const app = new Koa();
app.use(require('koa-static')('../frontend'));

app.use(bodyParser());

const router = new KoaRouter();

const LOG = {
  DEBUG: process.env.VERBOSE ? console.log : () => {},
  INFO: console.log,
  WARN: console.warn
};

function parseRollingId(scan) {
  const ct = scan && scan.contact_tracing;
  if (!ct) return null;
  // Note: both contact tracing protocols use 128-bit = 16-byte
  // rolling identifiers, which should not collide with each other
  // with any reasonable probability
  if (ct.apple_google_en) return ct.apple_google_en.rpi;
  return ct.dp3t_eph_id;
}

function generateRollingIdsAppleGoogleEN(request) {
  const { diagnosisKey, minUnixTime, maxUnixTime } = request;
  const rollingIds = cryptography.appleGoogle.diagnosisKeyToRPIs(
    Buffer.from(diagnosisKey, 'hex'),
    minUnixTime,
    maxUnixTime
  ).map((buf) => buf.toString('hex'));

  return { rollingIds, resolvedId: diagnosisKey };
}

function generateRollingIdsDP3T(request) {
  // ignoring start timestamp
  const { secretKey } = request;
  const rollingIds = cryptography.dp3t.secretKeyToEphIds(
    Buffer.from(secretKey, 'hex')
  ).map((buf) => buf.toString('hex'));

  return { rollingIds, resolvedId: secretKey };
}

function jsonArrayStream(ctx) {
  const s = new stream.Readable({ read() { } });
  ctx.response.body = s;
  ctx.response.status = 200;
  ctx.response.set('Content-Type', 'application/json');
  s.push('[');
  let first = true;
  return {
    each(row) {
      if (!first) s.push(',');
      if (row.json) row.json = JSON.parse(row.json);
      s.push(JSON.stringify(row));
      first = false;
    },
    finalize() {
      s.push(']');
      s.push(null);
    }
  };
}

async function resolveRollingIds(ctx, { resolvedId, rollingIds }) {
  if (!rollingIds || rollingIds.length === 0) {
    ctx.throw(400, 'No IDs generated');
  } else {
    LOG.INFO(`Resolving ${resolvedId} to ${rollingIds.length} rolling ID(s)`);
    rollingIds.forEach((id) => LOG.DEBUG(id));
    await db.updateResolved({ resolvedId, rollingIds });
    ctx.body = 'OK';
  }
}

router
  .post('/report', (ctx) => {
    const { agent, scan } = ctx.request.body;
    const rollingId = parseRollingId(scan);

    if (rollingId) {
      const row = {
        rollingId,
        contactJson: scan,
        agentId: agent.id,
        agentJson: agent
      };
      LOG.DEBUG(`inserting ${JSON.stringify(row)}`);
      // no await: fire & forget
      db.insert(row);
    } else {
      LOG.WARN('skipping report with no ID');
    }

    ctx.body = 'OK';
  })
  .post('/resolve/apple_google_en', async (ctx) => {
    await resolveRollingIds(ctx, generateRollingIdsAppleGoogleEN(ctx.request.body));
  })
  .post('/resolve/dp3t', async (ctx) => {
    await resolveRollingIds(ctx, generateRollingIdsDP3T(ctx.request.body));
  })
  .get('/all', (ctx) => {
    const s = jsonArrayStream(ctx);
    db.getAll(s.each, s.finalize);
  })
  .get('/resolved', (ctx) => {
    const s = jsonArrayStream(ctx);
    db.getResolved(s.each, s.finalize);
  });

app.use(router.routes());
app.use(router.allowedMethods());

const PORT = process.env.PORT || 3000;
const BIND_IP = process.env.BIND_IP || '127.0.0.1';
app.listen(PORT, BIND_IP, () => LOG.INFO(`started on port ${PORT}, bind ${BIND_IP}`));
