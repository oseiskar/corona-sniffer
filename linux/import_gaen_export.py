#!/usr/bin/python
"""
Decode a GAEN export.bin file to JSON.

Prerequisites: pip install protowire
Usage ./import_gaen_export.py < /path/to/export.bin
"""
import argparse, time
from protowire.proto_decoding import parse_stream_with_spec, parse_spec

def parse_gaen_export_bin(in_stream):
    # see https://github.com/google/exposure-notifications-server/blob/v0.6.1/examples/export/README.md
    # and https://github.com/google/exposure-notifications-server/blob/v0.6.1/internal/pb/export/export.proto
    in_stream.read(16) # drop first 16 bytes
    doc = parse_stream_with_spec(in_stream, parse_spec('1:fixed64,2:fixed64,7:[1:hex]'))
    for e in doc['7']:
        yield({
            'diagnosisKey': e['1'],
            'minUnixTime': doc['1'],
            'maxUnixTime': doc['2'],
        })

if __name__ == '__main__':
    import argparse, json, sys
    p = argparse.ArgumentParser(__doc__)
    p.add_argument('--server', help='server URL, .e.g, http://localhost:3000/resolve/apple_google_en')
    args = p.parse_args()

    for entry in parse_gaen_export_bin(sys.stdin):
        if args.server is not None:
            from agent import post
            post(entry, args.server, debug=True)
        else:
            print(json.dumps(entry))
