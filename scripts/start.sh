#!/bin/bash
# Start the statistics

java -Xmx1G -jar analyst-stats.jar /etc/stats.conf > /home/ubuntu/stats.log < /dev/null 2>&1 &
echo $! > /tmp/STATS_PID
