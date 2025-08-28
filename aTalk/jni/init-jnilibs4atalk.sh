#!/bin/bash
echo "### Initialize aTalk jni libraries for vpx, opus, speex/ogg and bcg729 ###"
# pushd vpx || exit
#   ./gen_abimk4vpx.sh # for ndk build
# popd || return

pushd opus || exit
  ./init_libopus.sh
popd || return

pushd speex || exit
  ./init_libSpeexOgg.sh
popd || return

pushd g729 || exit
  ./init_libbcg729.sh
popd || return
