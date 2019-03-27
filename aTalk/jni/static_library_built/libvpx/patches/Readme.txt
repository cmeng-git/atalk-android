Site: https://android.googlesource.com/platform/external/libvpx/+/ca30a60d2d6fbab4ac07c63bfbf7bbbd1fe6a583

=================
libvpx: Cherry-pick 0d88e15 from upstream

Description from upstream:
Add visibility="protected" attribute for global variables referenced
in asm files.

During aosp builds with binutils-2.27, we're seeing linker error
messages of this form:
libvpx.a(subpixel_mmx.o): relocation R_386_GOTOFF against preemptible
symbol vp8_bilinear_filters_x86_8 cannot be used when making a shared
object

subpixel_mmx.o is assembled from "vp8/common/x86/subpixel_mmx.asm".
Other messages refer to symbol references from deblock_sse2.o and
subpixel_sse2.o, also assembled from asm files.

This change marks such symbols as having "protected" visibility. This
satisfies the linker as the symbols are not preemptible from outside
the shared library now, which I think is the original intent anyway.

Bug: 37955747
Test: lunch aosp_x86-userdebug && m

Change-Id: Ica100155c1b65dd44740941d4d3c25abf7b08487
====================

libvpx/vp8/common/x86/filter_x86.c[diff]
libvpx/vpx_dsp/deblock.c[diff]
libvpx/vpx_ports/mem.h[diff]
3 files changed
