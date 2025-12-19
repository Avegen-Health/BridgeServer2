#!/bin/bash
# Sleep to allow Tomcat/Spring to initialize/fail
sleep 90

echo "=============== TOMCAT LOG DIR LISTING ===============" >> /var/log/eb-engine.log
ls -alR /var/log/tomcat/ >> /var/log/eb-engine.log

echo "=============== DUMPING CATALINA.*.LOG ===============" >> /var/log/eb-engine.log
tail -n 2000 /var/log/tomcat/catalina.*.log >> /var/log/eb-engine.log

echo "=============== DUMPING LOCALHOST_ACCESS_LOG ===============" >> /var/log/eb-engine.log
tail -n 2000 /var/log/tomcat/localhost_access_log.txt >> /var/log/eb-engine.log

echo "=============== DUMPING CATALINA.OUT (LAST 2000 LINES) ===============" >> /var/log/eb-engine.log
if [ -f /var/log/tomcat/catalina.out ]; then
    tail -n 2000 /var/log/tomcat/catalina.out >> /var/log/eb-engine.log
else
    echo "catalina.out NOT FOUND in /var/log/tomcat/" >> /var/log/eb-engine.log
    ls -R /var/log/tomcat >> /var/log/eb-engine.log
fi
echo "===================================================================" >> /var/log/eb-engine.log
