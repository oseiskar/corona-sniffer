#!/usr/bin/python
"""
Parse Android log data. Usage:

    cd data
    adb pull /storage/emulated/0/Android/data/org.example.coronasniffer/cache/logs
    cd ..
    cat data/logs/* | python parse_logs.py

"""
import datetime, json, sys

def parse_android_log_line(line):
    parts = line.strip().split(' ')
    if len(parts) < 5: return None # invalid / partial line
    date, time, _, level_and_tag = parts[:4]
    msg = ' '.join(parts[4:])
    level, _, tag = level_and_tag.partition('/')

    return {
        't': date + 'T' + time, # does not include year
        'level': level,
        'tag': tag,
        'msg': msg
    }

def parse_app_log(entry):
    # logs files written by the library seem arbitrarily partitioned,
    # this should drop all incorrect lines as long as we are only intersted
    # in JSON-formatted payloads
    try:
        msg = json.loads(entry['msg'])
        if msg is None: return None
    except:
        return None

    # pick certain log level(s)
    if entry['level'] != 'I': return None


    first = msg['first']
    if 'latitude' not in first: return None

    lat, lng = [first[f] for f in ['latitude', 'longitude']]

    return {
        'scan': {
            'contact_tracing': {
                'apple_google_en': {
                    'rpi': msg['rpi'],
                    'aem': msg['aem'],
                }
            }
        },
        'agent': {
          'id': "android-location-%f,%f" % (lat, lng),
          'location': {
            'latitude': lat,
            'longitude': lng
          }
        }
    }

if __name__ == '__main__':
    import argparse
    p = argparse.ArgumentParser(__doc__)
    p.add_argument('--server', help='server URL')
    p.add_argument('--debug', action='store_true',
        help='Debug mode: crash on HTTP errors, output extra logs')
    args = p.parse_args()

    def consume(msg):
        if args.server is None:
            print(json.dumps(msg))
        else:
            sys.path.append('../linux')
            from agent import post
            url = args.server
            if args.debug: print('POSTing %s to %s' % (msg, url))
            post(msg, url, args.debug)

    for line in sys.stdin:
        entry = parse_android_log_line(line)
        if entry is None: continue
        report = parse_app_log(entry)
        if report is None: continue
        consume(report)
