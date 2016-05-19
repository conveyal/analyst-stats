#!/bin/bash
# Start the statistics

java8 -Xmx1G -jar /opt/analyst-stats.jar /etc/stats.conf > /home/ec2-user/stats.log < /dev/null 2>&1 &
echo $! > /tmp/STATS_PID
