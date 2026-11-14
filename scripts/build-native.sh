#!/usr/bin/env bash
# 仅在用户本地执行：真实交叉编译，不下载/安装 NDK，不创建替代二进制。
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
: "${ANDROID_NDK_HOME:?请指向 Android NDK 28.2.13676358}"
TASK_NDK="$(cd "$ANDROID_NDK_HOME" && pwd)"
if ! python3 - "$TASK_NDK/source.properties" <<'PY'
from pathlib import Path
import sys
sys.exit(0 if 'Pkg.Revision = 28.2.13676358' in Path(sys.argv[1]).read_text().splitlines() else 1)
PY
then
    echo '需要精确版本 NDK 28.2.13676358（r28c）' >&2; exit 1
fi
case "$(uname -s)" in
    Linux) TASK_HOST=linux-x86_64 ;;
    Darwin) TASK_HOST=darwin-x86_64 ;;
    *) echo '使用 Linux 或 macOS 的本地 Bash 环境' >&2; exit 1 ;;
esac
TASK_TOOLCHAIN="$TASK_NDK/toolchains/llvm/prebuilt/$TASK_HOST"
export PATH="$TASK_TOOLCHAIN/bin:$PATH"
for task_command in python3 git make pkg-config clang llvm-readelf; do
    command -v "$task_command" >/dev/null || { echo "缺少本地工具 $task_command" >&2; exit 1; }
done
python3 "$TASK_ROOT/scripts/fetch-native-sources.py"
TASK_PREFIX="$TASK_ROOT/.native/prefix/arm64-v8a"
TASK_WORK="$TASK_ROOT/.native/build/arm64-v8a"
mkdir -p "$TASK_PREFIX" "$TASK_WORK/lame" "$TASK_WORK/ffmpeg"
TASK_CC="$TASK_TOOLCHAIN/bin/aarch64-linux-android33-clang"
TASK_CXX="$TASK_TOOLCHAIN/bin/aarch64-linux-android33-clang++"
TASK_LINK_FLAGS='-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384'
export CC="$TASK_CC" CXX="$TASK_CXX" AR=llvm-ar RANLIB=llvm-ranlib STRIP=llvm-strip NM=llvm-nm
export CFLAGS='-O2 -fPIC' CXXFLAGS='-O2 -fPIC' LDFLAGS="$TASK_LINK_FLAGS"
cd "$TASK_WORK/lame"
bash "$TASK_ROOT/.native/src/lame/configure" --host=aarch64-linux-android --prefix="$TASK_PREFIX" \
    --enable-shared --disable-static --disable-frontend --disable-decoder --disable-nasm
# LAME 的旧 libtool 默认 .so.0。修改生成的 libtool，真实链接为 Android 可加载的 .so；不改名冒充库。
python3 - <<'PY'
from pathlib import Path
path = Path('libtool')
text = path.read_text()
old = 'version_type=linux'
if old not in text:
    raise SystemExit('LAME libtool 模板改变，停止而非生成未检查的 SONAME')
path.write_text(text.replace(old, 'version_type=none'))
PY
make -j"${NATIVE_JOBS:-2}"
make install
export PKG_CONFIG_LIBDIR="$TASK_PREFIX/lib/pkgconfig"
unset PKG_CONFIG_PATH
cd "$TASK_WORK/ffmpeg"
bash "$TASK_ROOT/.native/src/ffmpeg/configure" \
    --prefix="$TASK_PREFIX" --target-os=android --arch=aarch64 --cpu=armv8-a --enable-cross-compile \
    --cc="$TASK_CC" --cxx="$TASK_CXX" --ar=llvm-ar --ranlib=llvm-ranlib --strip=llvm-strip --nm=llvm-nm \
    --sysroot="$TASK_TOOLCHAIN/sysroot" --pkg-config=pkg-config \
    --extra-cflags="-O2 -fPIC -I$TASK_PREFIX/include" --extra-ldflags="$TASK_LINK_FLAGS -L$TASK_PREFIX/lib" \
    --disable-everything --disable-autodetect --disable-static --enable-shared --enable-pic --enable-small \
    --disable-programs --disable-doc --disable-debug --disable-network --disable-avdevice --disable-swscale --disable-postproc \
    --disable-gpl --disable-nonfree --disable-version3 --enable-avformat --enable-avcodec --enable-avutil --enable-swresample --enable-avfilter \
    --enable-libmp3lame --enable-encoder=libmp3lame --enable-protocol=file \
    --enable-demuxer=mov,mp3,wav,flac,ogg,matroska,asf,aac \
    --enable-decoder=aac,aac_latm,mp3,mp3float,flac,alac,vorbis,opus,wmav1,wmav2,pcm_s16le,pcm_s16be,pcm_s24le,pcm_s24be,pcm_s32le,pcm_s32be,pcm_f32le,pcm_f32be,pcm_f64le,pcm_u8 \
    --enable-parser=aac,aac_latm,mpegaudio,flac,opus,vorbis --enable-muxer=mp3,wav \
    --enable-filter=loudnorm,aresample,aformat,atrim,asetpts,concat,anull
make -j"${NATIVE_JOBS:-2}"
make install
python3 "$TASK_ROOT/scripts/inspect-native.py" "$TASK_PREFIX/lib"
mkdir -p "$TASK_PREFIX/notices"
cp "$TASK_ROOT/native/sources.lock.json" "$TASK_PREFIX/notices/"
cp "$TASK_WORK/ffmpeg/ffbuild/config.mak" "$TASK_PREFIX/notices/ffmpeg-config.mak"
cp "$TASK_ROOT/.native/src/lame/COPYING" "$TASK_PREFIX/notices/LAME-COPYING"
cp "$TASK_ROOT/.native/src/ffmpeg/COPYING.LGPLv2.1" "$TASK_PREFIX/notices/FFmpeg-COPYING.LGPLv2.1"
if [ -f "$TASK_NDK/NOTICE" ]; then
    cp "$TASK_NDK/NOTICE" "$TASK_PREFIX/notices/NDK-NOTICE"
    mkdir -p "$TASK_ROOT/.native/runtime-notices"
    cp "$TASK_NDK/NOTICE" "$TASK_ROOT/.native/runtime-notices/NDK-NOTICE.txt"
else
    echo '未找到此NDK的NOTICE，停止交付，请核对安装完整性' >&2; exit 1
fi
echo '原生依赖已构建；还需本地构建 JNI/APK 并检查 ELF、APK 16 KB 对齐和设备运行。'
