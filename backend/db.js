const sqlite3 = require('sqlite3').verbose();

function createTablesIfNeeded(db) {
  return new Promise((resolve, reject) => {
    db.serialize(() => {
      db
        .run('PRAGMA journal_mode = WAL')
        .run('PRAGMA synchronous = NORMAL')
        .run(`
        CREATE TABLE IF NOT EXISTS agents (
          id text PRIMARY KEY UNIQUE,
          json text NOT NULL
        )`)
        .run(`
        CREATE TABLE IF NOT EXISTS contacts (
          rolling_id text NOT NULL,
          agent_id text NOT NULL,
          time text NOT NULL,
          json text NOT NULL,
          resolved_id text
        )`)
        .run(`
        CREATE INDEX IF NOT EXISTS index_rolling_id
          ON contacts(rolling_id)`)
        .run(`
        CREATE INDEX IF NOT EXISTS index_resolved_id
          ON contacts(resolved_id)
          WHERE resolved_id IS NOT NULL
        `, (err) => (err ? reject(err) : resolve(true)));
    });
  });
}

const validated = {
  id(str) {
    const MAX_ID_LENGTH = 100;
    if (!str) throw new Error('missing ID');
    if (str.length > MAX_ID_LENGTH) throw new Error('ID max length exceeded');
    return str;
  },
  json(obj) {
    const MAX_JSON_LENGTH = 2000;
    const str = JSON.stringify(obj);
    if (str.length > MAX_JSON_LENGTH) throw new Error('JSON max length exceeded');
    return str;
  }
};

function databaseApi(db) {
  function promiseRun(...args) {
    // console.log(...args);
    return new Promise((resolve, reject) => db.run(...args,
      function cb(err) { return (err ? reject(err) : resolve(this)); }));
  }

  return {
    insert({
      rollingId, contactJson, agentId, agentJson
    }) {
      const timeStr = (new Date()).toISOString();
      return Promise.all([
        promiseRun(`
          INSERT INTO agents (id, json) VALUES (?, ?) ON CONFLICT(id) DO NOTHING`,
        [
          validated.id(agentId),
          validated.json(agentJson)
        ]),
        promiseRun(`
          INSERT INTO contacts (rolling_id, agent_id, time, json) VALUES (?, ?, ?, ?)`,
        [
          validated.id(rollingId),
          validated.id(agentId),
          timeStr,
          validated.json(contactJson)
        ])
      ]);
    },
    updateResolved({ resolvedId, rollingIds }) {
      const inList = rollingIds.map(() => '?').join(',');
      return promiseRun(`
        UPDATE contacts SET resolved_id = ? WHERE rolling_id IN (${inList})`,
      [validated.id(resolvedId)].concat(rollingIds.map(validated.id))).then((result) => {
        if (result.changes) {
          console.log(`resolved ID ${resolvedId} to ${result.changes} row(s)`);
        }
      });
    },
    getResolved(each, finalize) {
      db.each(`
        SELECT * FROM contacts
        INNER JOIN agents ON contacts.agent_id = agents.id
        WHERE contacts.resolved_id IS NOT NULL
      `,
      (err, result) => {
        if (err) throw err;
        each(result);
      }, finalize);
    },
    getAll(cb, finalize) {
      db.each(`
        SELECT * FROM contacts
        INNER JOIN agents ON contacts.agent_id = agents.id
      `,
      (err, result) => {
        if (err) throw err;
        cb(result);
      }, finalize);
    },
    clearAll() {
      console.log('clearing database');
      return Promise.all([
        'DROP TABLE contacts',
        'DROP TABLE agents'
      ].map((stm) => promiseRun(stm)))
        .then(() => createTablesIfNeeded(db));
    },
    transaction(cb) {
      db.serialize(() => {
        cb(this);
      });
    }
  };
}

function createAndConnect() {
  const dbFile = process.env.DATABASE_FILE || 'data/database.db';
  const db = new sqlite3.Database(dbFile);
  createTablesIfNeeded(db);
  return databaseApi(db);
}

module.exports = createAndConnect();
