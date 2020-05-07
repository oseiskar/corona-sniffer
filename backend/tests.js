const assert = require('assert');
const cryptography = require('./cryptography');

(function testAES128() {
  // see https://kavaliro.com/wp-content/uploads/2014/03/AES.pdf
  const plainText = cryptography.keyFromString('Two One Nine Two');
  const key = cryptography.keyFromString('Thats my Kung Fu');
  const ciphertext = cryptography.aes128(key, plainText);
  const expected = Buffer.from('29C3505F571420F6402299B31A02D73A', 'hex');
  assert(ciphertext.equals(expected));
}());

(function testRollingProximityIDs() {
  const exposureKey = cryptography.keyFromString('foo');
  const rpik = cryptography.exposureKeyToRPIK(exposureKey);
  const timeBegin = 0;
  const timeEnd = 60 * 10 + 1;
  const result = cryptography.exposureKeyToRollingProximityIDs(exposureKey, timeBegin, timeEnd);
  assert(result.length === 2);
  const padded0 = Buffer.from('454e2d52504900000000000000000000', 'hex');
  const padded1 = Buffer.from('454e2d52504900000000000001000000', 'hex');
  const proxID0 = cryptography.aes128(rpik, padded0);
  const proxID1 = cryptography.aes128(rpik, padded1);
  console.log(proxID0.toString('hex'));
  console.log(proxID1.toString('hex'));
  assert(result[0].equals(proxID0));
  assert(result[1].equals(proxID1));
}());
