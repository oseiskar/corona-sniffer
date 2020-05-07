const assert = require('assert');
const crypto = require('crypto');
const hkdf = require('futoin-hkdf');

/*
 * Implements Apple & Google:
 *
 * Exposure Notification Cryptography Specification Preliminary
 * April 2020 v1.2
 */
const KEY_LENGTH_BYTES = 16;
const EN_PRI = Buffer.from('EN-RPI', 'utf-8');
const ZERO_AES_IV = Buffer.alloc(KEY_LENGTH_BYTES, 0);

function ENIntervalNumber(unixTimeSeconds) {
  return Math.floor(unixTimeSeconds / (60 * 10));
}

function generatePaddedData(j) {
  const data = Buffer.alloc(KEY_LENGTH_BYTES);
  EN_PRI.copy(data);
  data.writeUInt32LE(j, 12);
  return data;
}

function closedRange(begin, end) {
  const r = [];
  for (let j = begin; j <= end; ++j) {
    r.push(j);
  }
  return r;
}

function aes128(key, data) {
  assert(key.length === KEY_LENGTH_BYTES);
  assert(data.length === KEY_LENGTH_BYTES);
  const cipher = crypto
    .createCipheriv('aes-128-cbc', key, ZERO_AES_IV)
    .setAutoPadding(false);
  const ciphertext = cipher.update(data);
  cipher.final();
  assert(ciphertext.length === KEY_LENGTH_BYTES);
  return ciphertext;
}

function exposureKeyToRPIK(exposureKey) {
  assert(exposureKey.length === KEY_LENGTH_BYTES);
  return hkdf(exposureKey, KEY_LENGTH_BYTES, {
    info: Buffer.from('EN-RPIK'),
    hash: 'SHA-256'
  });
}

function exposureKeyToRollingProximityIDs(exposureKey, unixTimeBegin, unixTimeEnd) {
  const rpik = exposureKeyToRPIK(exposureKey);
  const jBegin = ENIntervalNumber(unixTimeBegin);
  const jEnd = ENIntervalNumber(unixTimeEnd);
  const jRange = closedRange(jBegin, jEnd);
  return jRange.map((j) => aes128(rpik, generatePaddedData(j)));
}

function keyFromString(string) {
  const data = Buffer.alloc(KEY_LENGTH_BYTES);
  Buffer.from(string, 'utf-8').copy(data);
  return data;
}

module.exports = {
  exposureKeyToRollingProximityIDs,
  exposureKeyToRPIK,
  keyFromString,
  aes128
};
