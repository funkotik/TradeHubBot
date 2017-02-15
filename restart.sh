#!/bin/bash
#git pull
kill -9 $(cat target/universal/tradehubbot-1.0/RUNNING_PID )
rm -r target/universal/tradehubbot-1.0
sbt dist
unzip target/universal/tradehubbot-1.0.zip
mv tradehubbot-1.0 target/universal/tradehubbot-1.0
cd target/universal/
nohup tradehubbot-1.0/bin/tradehubbot -Dhttps.port=9001 -J-Xms2240M -J-Xmx16000M -Dconfig.resource=production.conf -J-server&
tail -f --lines=100 nohup.out