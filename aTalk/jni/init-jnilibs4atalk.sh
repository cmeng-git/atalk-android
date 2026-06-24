#!/bin/bash
echo "### Initialize aTalk jni libraries for opus, and speex/ogg ###"
# pushd vpx || exit
#   ./gen_abimk4vpx.sh # for ndk build
# popd || return

pushd opus || exit
  ./init_libopus.sh
popd || return

pushd speex || exit
  ./init_libSpeexOgg.sh
popd || return
