#!/bin/bash
# Stop the stats collector

if [ -e /tmp/STATS_PID ]; then
  kill `cat /tmp/STATS_PID`
fi
