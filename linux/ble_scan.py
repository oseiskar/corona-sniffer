#!/usr/bin/python
"""
BLE sniffer for Linux. Requires hcidump and hcitool executables to
be available (e.g., apt-get install bluez-hcidump). Run as root.
Works with Python 2.7 (which is still the likely version of system Python)

If you get

    Set scan parameters failed: Input/output error

try to fix with:

    hciconfig hci0 down; hciconfig hci0 up

This is presumably caused by random bugs in the Linux Bluetooth stack.
See https://stackoverflow.com/a/23059924/1426569
"""
import subprocess
import binascii
import struct
import time

def parse_ibeacon(ads):
    if len(ads) != 1 or ads[0].type != '\xff': return None
    msg = ads[0].data
    if msg[:3] != '\x4c\x00\x02': return None
    l = ord(msg[3])
    payload = msg[4:(4+l)]
    uh = binascii.hexlify(payload[:16])
    b = {
        'uuid': '-'.join([uh[:8], uh[8:12], uh[12:16], uh[16:20], uh[20:]]),
        'major': struct.unpack('>h', payload[16:18])[0],
        'minor': struct.unpack('>h', payload[18:20])[0],
    }
    if l >= 21: b['txpower'] = struct.unpack('b', payload[20])[0]
    return b

def parse_ad_structures(msg):
    class ADStruct: pass
    i = 0
    ads = []
    while i < len(msg):
        ad_len = ord(msg[i])
        if ad_len == 0: break
        i += 1
        s = ADStruct()
        s.type = msg[i]
        s.data = msg[(i+1):(i+ad_len)]
        i += ad_len
        ads.append(s)
    return ads

def parse_ble_adv_msg(msg):
    # see BLE specs, if you can find them
    # 04 = HCI Event
    # 3E = LE Advertising Report
    HCI_LE_ADV_REPORT = '\x04\x3e'
    if msg[:2] != HCI_LE_ADV_REPORT: return None

    # Then one byte "Total parameter length"
    total_len = ord(msg[2])
    # The rest is defined in
    # Bluetooth Specification v5.0, Vol 2, Part E, sec 7.7.65.2
    le_adv_params = msg[3:(3+total_len)]

    subev = le_adv_params[0]
    HCI_LE_ADV_REPORT_SUBEV = '\x02'
    if subev != HCI_LE_ADV_REPORT_SUBEV: return None

    n_responses = ord(le_adv_params[1])

    # assume there's only one (TODO)
    if n_responses != 1: return None

    class BLEAdvPacket: pass
    packet = BLEAdvPacket()

    packet.type = {
        '\x00': 'ADV_IND',
        '\x01': 'ADV_DIRECT_IND',
        '\x02': 'ADV_SCAN_IND',
        '\x03': 'ADV_NONCONN_IND',
        '\x04': 'SCAN_RSP'
    }[le_adv_params[2]]

    packet.raw_hci = msg
    packet.addr_type = {
        '\x00': 'Public',
        '\x01': 'Random',
        '\x02': 'Public ID',
        '\x03': 'Random Static ID'
    }[le_adv_params[3]]
    packet.hw_addr = le_adv_params[4:(4+6)]

    response_length = ord(le_adv_params[10])
    packet.raw_event = le_adv_params[11:(11+response_length)]
    packet.ads = parse_ad_structures(packet.raw_event)
    packet.rssi = struct.unpack('b', le_adv_params[11+response_length])[0]
    packet.ibeacon = parse_ibeacon(packet.ads)

    return packet

class Scanner:
    def __init__(self):
        try:
            from subprocess import DEVNULL # py3k
        except ImportError:
            import os
            DEVNULL = open(os.devnull, 'wb')

        self.scanner = subprocess.Popen(
            ['hcitool', 'lescan', '--duplicates'], stdout=DEVNULL)

        self.hcidump = subprocess.Popen(['hcidump', '--raw'], stdout=subprocess.PIPE)
        self.line = ''

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        self.hcidump.kill()
        self.scanner.kill()
        print('killed')

    def read_line(self):
        return self.hcidump.stdout.readline().strip()

    def read_single(self):
        while '>' not in self.line:
            self.line = self.read_line() # ignore
            if self.line is None: return None
        buf = ''
        while True:
            buf += self.line.replace('>', '').replace(' ', '')
            self.line = self.read_line()
            if self.line is None: return None
            if '>' in self.line: break
        try:
            return binascii.unhexlify(buf)
        except:
            time.sleep(0.01) # avoid busy loop if stuck
            return ''

    def read(self):
        while True:
            b = self.read_single()
            if b is None: return None
            if len(b) > 0:
                msg = parse_ble_adv_msg(b)
                if msg is not None: return msg

def message_as_json(msg, raw=False, text=False):
    h = binascii.hexlify
    if text:
        enc = lambda x: ''.join([chr(ord(c)) if 32 <= ord(c) <= 127 else '.' for c in x])
    else:
        enc = h

    obj = {
        'rssi': msg.rssi,
        'mac': h(msg.hw_addr),
        'mac_type': msg.addr_type,
        'type': msg.type,
        'data': [{
            'type': h(a.type),
            'data': enc(a.data)
        } for a in msg.ads ],
        'ibeacon': msg.ibeacon
    }
    if raw:
        obj['raw_hci'] = h(msg.raw_hci),
        obj['raw_event'] = h(msg.raw_event)
    else:
        if obj['ibeacon'] is not None:
            del obj['data']
    if obj['ibeacon'] is None:
        del obj['ibeacon']
    return obj

if __name__ == '__main__':
    import json
    import argparse
    p = argparse.ArgumentParser(__doc__)
    p.add_argument('--raw', action='store_true',
        help='Also output raw HCI and other unparsed hex data')
    p.add_argument('--text', action='store_true',
        help='Try to decode text in manufacturer data')
    args = p.parse_args()

    with Scanner() as scan:
        while True:
            print(json.dumps(message_as_json(scan.read(), **vars(args))))
