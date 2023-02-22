#!/bin/bash

echo "### Fetching aTalk jni libraries source for speex, ogg, and opus ###"

pushd opus || exit
  echo "### Fetching opus library source v1.3.1 ###"
  ./init_libopus.sh
popd || return

pushd speex || exit
  echo "### Fetching speex (speex-1.2rc1 & v1.2.1) and ogg (1.3.5) libraries source ###"
  ./init_libSpeexOgg.sh
popd || return
