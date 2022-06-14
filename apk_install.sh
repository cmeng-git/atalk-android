#!/bin/bash
# set -x

echo "### Manually install aTalk-playstore-debug.apk the device ###"

adb push /home/cmeng/workspace/android/atalk-android/aTalk/build/outputs/apk/playstore/debug/aTalk-playstore-debug.apk /data/local/tmp/aTalk-playstore-debug.apk
adb shell pm install -t /data/local/tmp/aTalk-playstore-debug.apk

