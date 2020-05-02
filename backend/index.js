const stream = require('stream');
const Koa = require('koa');
const KoaRouter = require('koa-router');
const db = require('./db');

const app = new Koa();
app.use(require('koa-static')('../frontend'));

const router = new KoaRouter();

function parseRollingId(scan) {
  return scan.id;
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
    const { client, scan } = JSON.parse(ctx.request.body);
    const rollingId = parseRollingId(scan);
    // no await: fire & forget
    db.insert({
      rollingId,
      contactJson: scan,
      agentId: client.id,
      agentJson: client
    });
    ctx.body = 'OK';
  })
  .post('/resolve', async (ctx) => {
    const { resolvedId, rollingIds } = JSON.parse(ctx.request.body);
    await db.updateResolved({ resolvedId, rollingIds });
    ctx.body = 'OK';
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
app.listen(PORT, BIND_IP, () => console.log(`started on port ${PORT}, bind ${BIND_IP}`));
