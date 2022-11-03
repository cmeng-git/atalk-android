#!/bin/bash

echo "### Generate single monolithic hymnchtv-debug.apk from ./debug/hymnchtv-debug.aab, for remote installation ###"

java -jar ../bundletool.jar build-apks --bundle=./aTalk/build/outputs/bundle/fdroidDebug/aTalk-fdroid-debug.aab --output=./aTalk/build/outputs/bundle/fdroidDebug/aTalk-fdroid-debug.apks --overwrite --local-testing --mode=universal

pushd ~/workspace/android/atalk-android/aTalk/build/outputs/bundle/fdroidDebug/ || exit

apktool d -f -s aTalk-fdroid-debug.apks -o ./tmp
mv ./tmp/unknown/universal.apk aTalk-fdroid-debug.apk

popd || return
