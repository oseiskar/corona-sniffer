const Koa = require('koa');
const koaBody = require('koa-body');
const db = require('./db');

const app = new Koa();

app.use(koaBody());
app.use(ctx => {
  const { client, scan } = ctx.request.body;
  const rollingId = parseRollingId(scan);

  console.log(`received ${JSON.stringify(ctx.request.body)}`);
  ctx.body = `OK`;
});

const PORT = process.env.PORT || 3000;
const BIND_IP = process.env.BIND_IP || '127.0.0.1';
app.listen(PORT, BIND_IP, () => console.log(`started on port ${PORT}, bind ${BIND_IP}`));
