#!/bin/bash

echo "### Fetching aTalk jni libraries source for speex, ogg, opus and bcg729 ###"

pushd opus || exit
  echo "### Fetching opus library source v1.3.1 ###"
  ./init_libopus.sh
popd || return

pushd speex || exit
  echo "### Fetching speex (speex-1.2rc1 & v1.2.1) and ogg (1.3.5) libraries source ###"
  ./init_libSpeexOgg.sh
popd || return

pushd g729 || exit
  echo "### Fetching g729 (bcg729-1.1.1) libraries source ###"
  ./init_libbcg729.sh
popd || return
