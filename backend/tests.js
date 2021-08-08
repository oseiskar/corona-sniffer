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

(function testAppleGoogleRollingProximityIDs() {
  const exposureKey = cryptography.keyFromString('foo');
  const rpik = cryptography.appleGoogle.exposureKeyToRPIK(exposureKey);
  const timeBegin = 0;
  const timeEnd = 60 * 10 + 1;
  const result = cryptography.appleGoogle.diagnosisKeyToRPIs(exposureKey, timeBegin, timeEnd);
  assert(result.length === 2);
  const padded0 = Buffer.from('454e2d52504900000000000000000000', 'hex');
  const padded1 = Buffer.from('454e2d52504900000000000001000000', 'hex');
  const proxID0 = cryptography.aes128(rpik, padded0);
  const proxID1 = cryptography.aes128(rpik, padded1);
  assert(result[0].equals(proxID0));
  assert(result[1].equals(proxID1));
}());


(function testDP3TEphIDs() {
  const secretKey = cryptography.keyFromString('example');
  const ephIds = cryptography.dp3t.secretKeyToEphIds(secretKey);
  assert(ephIds.length === 24 * 4);
  assert(ephIds[1].equals(Buffer.from('ce0a533565e9179847ef88fe20f7b5ca', 'hex')));
}());

// https://github.com/google/exposure-notifications-internals/blob/main/app/src/androidTest/java/com/google/samples/exposurenotification/testing/TestVectors.java
(function testAppleGoogleRollingProximityIDs() {
  const exposureKey = Buffer.from([
    0x75, 0xc7, 0x34, 0xc6, 0xdd, 0x1a, 0x78, 0x2d,
    0xe7, 0xa9, 0x65, 0xda, 0x5e, 0xb9, 0x31, 0x25]);
  const rpik = cryptography.appleGoogle.exposureKeyToRPIK(exposureKey);
  assert(rpik.equals(Buffer.from([
    0x18, 0x5a, 0xd9, 0x1d, 0xb6, 0x9e, 0xc7, 0xdd,
    0x04, 0x89, 0x60, 0xf1, 0xf3, 0xba, 0x61, 0x75])));
  const timeBegin = 1585785600;
  const timeEnd = timeBegin + 60 * 10 * 144;
  const result = cryptography.appleGoogle.diagnosisKeyToRPIs(exposureKey, timeBegin, timeEnd);
  // console.log(result.length);
  // console.log(result[0].toString('hex'));
  assert(result.length === 145);
  assert(result[0].equals(Buffer.from([
    0x8b, 0xe6, 0xcd, 0x37, 0x1c, 0x5c, 0x89, 0x16,
    0x04, 0xbf, 0xbe, 0x49, 0xdf, 0x84, 0x50, 0x96
  ])));
  assert(result[1].equals(Buffer.from([
    0x3c, 0x9a, 0x1d, 0xe5, 0xdd, 0x6b, 0x02, 0xaf,
    0xa7, 0xfd, 0xed, 0x7b, 0x57, 0x0b, 0x3e, 0x56
  ])));
  assert(result[143].equals(Buffer.from([
    0xf4, 0x31, 0xb6, 0x2e, 0xcf, 0x44, 0x31, 0x02,
    0xce, 0x4e, 0xd0, 0x40, 0x7d, 0xe5, 0x4b, 0xd4
  ])));
}());
