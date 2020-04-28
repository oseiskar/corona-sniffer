#!/usr/bin/python
"""
POST BLE scans to a remote server
"""
import json, sys, urllib2

def jsonlReader(infile, text_filter=''):
    for line in infile:
        if len(line) == 0: break
        if text_filter not in line: continue
        yield(json.loads(line.strip()))

def send(message, server, debug=False):
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
        help='Debug mode: crash on HTTP errors')
    p.add_argument('--filter', default='',
        help='Only accept JSON lines that contain this string (anywhere)')
    args = p.parse_args()

    with open(args.config) as f:
        client_config = json.load(f)

    for scan in jsonlReader(sys.stdin, text_filter=args.filter):
        msg = {
            'scan': scan,
            'client': client_config
        }
        send(msg, args.server, args.debug)
