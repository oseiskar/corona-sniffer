#!/bin/bash
# Run as root
FILTER=ibeacon
: "${SERVER:=http://localhost:3000/report}"
./ble_scan.py | ./agent.py \
  --config=client.json \
  --filter=ibeacon \
  --server=$SERVER
