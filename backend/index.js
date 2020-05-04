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
  // TODO: dummy iBeacon version
  return scan && scan.contact_tracing && scan.contact_tracing.rolling_id;
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
  .post('/resolve', async (ctx) => {
    const { resolvedId, minUnixTime, maxUnixTime } = ctx.request.body;
    const MAX_TIME_RANGE_DAYS = 30;
    const MAX_SECS = MAX_TIME_RANGE_DAYS * 24 * 60 * 60;
    if ((maxUnixTime - minUnixTime) > MAX_SECS) {
      ctx.throw(400, 'Max time range exceeded');
    } else {
      const rollingIds = cryptography.exposureKeyToRollingIdentifiers(
        Buffer.from(resolvedId, 'hex'),
        minUnixTime,
        maxUnixTime
      ).map((buf) => buf.toString('hex'));
      LOG.INFO(`Resolving ${resolvedId} to ${rollingIds.length} rolling ID(s)`);
      rollingIds.forEach((id) => LOG.DEBUG(id));
      await db.updateResolved({ resolvedId, rollingIds });
      ctx.body = 'OK';
    }
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
