#!/usr/bin/python
"""
A helper script to post diagnosis keys (exposure keys corresponding to
confirmed positive cases) to the server
"""
import argparse, time
from agent import post

unix_now = int(time.time())
default_margin = 2*60*60 # 2 hours
p = argparse.ArgumentParser(__doc__)
p.add_argument('--server', help='server URL',
    default='http://localhost:3000/resolve')
p.add_argument('protocol', choices=['dp3t', 'apple_google_en'])
p.add_argument('key_hex')
p.add_argument('-min', '--min_unix_time', type=int,
    default=unix_now - default_margin)
p.add_argument('-max', '--max_unix_time', type=int,
    default=unix_now + default_margin)
args = p.parse_args()

msg = {
    'apple_google_en': {
        'diagnosisKey': args.key_hex,
        'minUnixTime': args.min_unix_time,
        'maxUnixTime': args.max_unix_time
    },
    'dp3t': {
        'secretKey': args.key_hex
    }
}[args.protocol]

post(msg, args.server + '/' + args.protocol, debug=True)
