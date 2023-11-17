#!/bin/bash

echo "### Generate single monolithic atalk-debug.apk from ./debug/atalk-debug.aab, for remote installation ###"

if [[ $# -eq 1 ]]; then
  fnapk="aTalk-$1-debug"
  dirapk="$1Debug"
else
  dirapk="fdroidDebug"
fi

java -jar ../bundletool.jar build-apks --bundle=./aTalk/build/outputs/bundle/$dirapk/$fnapk.aab --output=./aTalk/build/outputs/bundle/$dirapk/$fnapk.apks --overwrite --local-testing --mode=universal

pushd ~/workspace/android/atalk-android/aTalk/build/outputs/bundle/$dirapk/ || exit

apktool d -f -s $fnapk.apks -o ./tmp
mv ./tmp/unknown/universal.apk ~/workspace/android/atalk-android/aTalk/release/$fnapk.apk
rm  $fnapk.apks

popd || return
