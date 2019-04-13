#!/bin/bash
#set -x
# Applying required patches for all the codec modules

# ===============================
# ffmpeg patches
# ===============================
if [[ -f "ffmpeg/ffbuild/version.sh" ]]; then
  VERSION=$(ffmpeg/ffbuild/version.sh ./ffmpeg)
else
  VERSION=$(ffmpeg/version.sh ./ffmpeg)
fi
X264_API="$(grep '#define X264_BUILD' < x264/x264.h | sed 's/^.* \([1-9][0-9]*\).*$/\1/')"

echo -e "### Applying patches for ffmpeg-v${VERSION} modules"

if [[ ${VERSION} == 1.0.10 ]]; then
    patch  -p0 -N --dry-run --silent -f ./ffmpeg/configure < ./patches/ffmpeg-1.0.10/10.ffmpeg-configure.patch 1>/dev/null
    if [[ $? -eq 0 ]]; then
      patch -p0 -f ./ffmpeg/configure < ./patches/ffmpeg-1.0.10/10.ffmpeg-configure.patch
    fi

    patch  -p0 -N --dry-run --silent -f ./ffmpeg/libavcodec/libx264.c < ./patches/ffmpeg-1.0.10/11.ffmpeg-libx264.c.patch 1>/dev/null
    if [[ $? -eq 0 ]]; then
      patch -p0 -f ./ffmpeg/libavcodec/libx264.c < ./patches/ffmpeg-1.0.10/11.ffmpeg-libx264.c.patch
    fi

    patch  -p0 -N --dry-run --silent -f ./ffmpeg/libavutil/pixdesc.c < ./patches/ffmpeg-1.0.10/12.ffmpeg-pixdesc.c.patch 1>/dev/null
    if [[ $? -eq 0 ]]; then
      patch -p0 -f ./ffmpeg/libavutil/pixdesc.c < ./patches/ffmpeg-1.0.10/12.ffmpeg-pixdesc.c.patch
    fi

    patch  -p0 -N --dry-run --silent -f ./ffmpeg/libavutil/pixdesc.h < ./patches/ffmpeg-1.0.10/13.ffmpeg-pixdesc.h.patch 1>/dev/null
    if [[ $? -eq 0 ]]; then
      patch -p0 -f ./ffmpeg/libavutil/pixdesc.h < ./patches/ffmpeg-1.0.10/13.ffmpeg-pixdesc.h.patch
    fi

    patch  -p0 -N --dry-run --silent -f ./ffmpeg/libavcodec/svq3.c < ./patches/ffmpeg-1.0.10/14.ffmpeg-svq3.c.patch 1>/dev/null
    if [[ $? -eq 0 ]]; then
      patch -p0 -f ./ffmpeg/libavcodec/svq3.c < ./patches/ffmpeg-1.0.10/14.ffmpeg-svq3.c.patch
    fi
elif [[ ${VERSION} == 3.4 ]]; then
    patch  -p0 -N --dry-run --silent -f ./ffmpeg/libavcodec/aaccoder.c < ./patches/01.ffmpeg_aacoder.patch 1>/dev/null
    if [[ $? -eq 0 ]]; then
      patch -p0 -f ./ffmpeg/libavcodec/aaccoder.c < ./patches/01.ffmpeg_aacoder.patch
    fi

    patch  -p0 -N --dry-run --silent -f ./ffmpeg/libavcodec/hevc_mvs.c < ./patches/02.ffmpeg_hevc_mvs.patch 1>/dev/null
    if [[ $? -eq 0 ]]; then
      patch -p0 -f ./ffmpeg/libavcodec/hevc_mvs.c < ./patches/02.ffmpeg_hevc_mvs.patch
    fi

    patch  -p0 -N --dry-run --silent -f ./ffmpeg/libavcodec/opus_pvq.c < ./patches/03.ffmpeg_opus_pvq.patch 1>/dev/null
    if [[ $? -eq 0 ]]; then
      patch -p0 -f ./ffmpeg/libavcodec/opus_pvq.c < ./patches/03.ffmpeg_opus_pvq.patch
    fi

    patch  -p0 -N --dry-run --silent -f ./ffmpeg/libaudevice/v4l2.c < ./patches/04.ffmpeg_v4l2.patch 1>/dev/null
    if [[ $? -eq 0 ]]; then
      patch -p0 -f ./ffmpeg/libaudevice/v4l2.c < ./patches/03.ffmpeg_v4l2.patch
    fi

    # need to patch ffmpeg (v3.4) libx264c if libvpx vesion is > 152
    if [[ ${X264_API} > 152 ]]; then
        patch  -p0 -N --dry-run --silent -f ./ffmpeg/libavcodec/libx264.c < ./patches/05.ffmpeg_libx264.patch 1>/dev/null
        if [[ $? -eq 0 ]]; then
          patch -p0 -f ./ffmpeg/libavcodec/libx264.c < ./patches/05.ffmpeg_libx264.patch
        fi
    fi
fi


# ===============================
# x264 patches
# ===============================
echo -e "### Applying patches for x264-v${X264_API} modules"

patch  -p0 -N --dry-run --silent -f ./x264/configure < ./patches/21.x264_configure.patch 1>/dev/null
if [[ $? -eq 0 ]]; then
  patch -p0 -f ./x264/configure < ./patches/21.x264_configure.patch
fi
