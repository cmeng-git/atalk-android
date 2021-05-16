#!/bin/bash
. _settings.sh

# defined modules to be included in ffmpeg-atalk built
# x264 and h264 are mutually exclusive
# MODULES=("x264" "lame")
MODULES=("x264")

# Build only the specified module if given as second parameter
if [[ $# -eq 2 ]]; then
  MODULES=("$2")
fi

# Auto fetch and unarchive both ffmpeg and x264 from online repository
[[ -d ffmpeg ]] && [[ -d x264 ]] || ./init_update_libs.sh

# Applying required patches
. ffmpeg-atalk_patch.sh "${MODULES[@]}"

for ((i=0; i < ${#ABIS[@]}; i++))
  do
    if [[ $# -eq 0 ]] || [[ "$1" == "${ABIS[i]}" ]]; then
      # Do not build 64-bit ABI if ANDROID_API is less than 21 - minimum supported API level for 64 bit.
      [[ ${ANDROID_API} < 21 ]] && ( echo "${ABIS[i]}" | grep 64 > /dev/null ) && continue;

      # $1 = architecture
      # $2 = required for proceed to start setup default compiler environment variables
      for m in "${MODULES[@]}"
      do
        case $m in
          h264)
            ./_h264_build.sh "${ABIS[i]}" $m || exit 1
          ;;
          x264)
            ./_x264_build.sh "${ABIS[i]}" $m || exit 1
          ;;
          lame)
            ./_lame_build.sh "${ABIS[i]}" $m || exit 1
          ;;
        esac
      done

      ./_ffmpeg_build.sh "${ABIS[i]}" 'ffmpeg' "${MODULES[@]}" || exit 1
    fi
  done

echo -e "*** BUILD COMPLETED ***\n"

