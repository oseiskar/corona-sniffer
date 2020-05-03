#!/bin/bash
# Run as root
FILTER=ibeacon
: "${SERVER:=http://localhost:3000/report}"
./ble_scan.py | ./agent.py \
  --config=agent.json \
  --filter=contact_tracing \
  --server=$SERVER # --debug
