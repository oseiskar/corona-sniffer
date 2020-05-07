#!/usr/bin/python
"""
POST BLE scans to a remote server
"""
import json, sys, urllib2

def jsonlReader(infile, field_filter=''):
    for line in infile:
        if len(line) == 0: break
        obj = json.loads(line.strip())
        if len(field_filter) > 0 and field_filter not in obj: continue
        yield(obj)

def post(message, server, debug=False):
    payload = json.dumps(message)
    if debug:
        print(payload)
    req = urllib2.Request(server, payload,
        {'Content-Type': 'application/json'})
    try:
        conn = urllib2.urlopen(req)
        conn.read() # read and ignore response
        conn.close()
    except Exception as e:
        if debug:
            raise e
        else:
            sys.stderr.write(str(e) + '\n')

if __name__ == '__main__':
    import argparse
    p = argparse.ArgumentParser(__doc__)
    p.add_argument('--config', help='client config JSON file')
    p.add_argument('--server', help='server URL')
    p.add_argument('--debug', action='store_true',
        help='Debug mode: crash on HTTP errors, output extra logs')
    p.add_argument('--filter', default='',
        help='Only accept JSON objects that contain this field')
    args = p.parse_args()

    with open(args.config) as f:
        agent_config = json.load(f)

    for scan in jsonlReader(sys.stdin, field_filter=args.filter):
        msg = {
            'scan': scan,
            'agent': agent_config
        }
        post(msg, args.server, args.debug)
