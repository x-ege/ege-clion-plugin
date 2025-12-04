#!/usr/bin/env bash

cd "$(dirname "$0")"

if [[ -n "$1" ]]; then
    export XEGE_DIR="$1"
else
    export XEGE_DIR="../xege"
fi

XEGE_DIR=$(realpath "$XEGE_DIR")

if [[ ! -e "$XEGE_DIR/.git" ]]; then
    echo "Usage: $0 [xege_dir]"
    echo "Where [xege_dir] is the path to the xege repository."
    echo "If not provided, it defaults to '../xege'."
    echo "Please ensure that the xege repository is initialized and contains the necessary files."
    echo "Current XEGE_DIR: $XEGE_DIR does not appear to be a valid git repository."
    exit 1
fi

echo "Find xege repository at $XEGE_DIR"

# Check if rsync is available
if ! command -v rsync >/dev/null 2>&1; then
    echo "Error: rsync is required but not installed."
    echo "Please install rsync to use this script."
    exit 1
fi

cd assets/ege_src || exit 1

# 拷贝 ege 源代码
rm -rf ./src ./include ./CMakeLists.txt
cp -rf "$XEGE_DIR/src" ./src
cp -rf "$XEGE_DIR/include" ./include
cp -rf "$XEGE_DIR/CMakeLists.txt" ./CMakeLists.txt

# 拷贝第三方 - ccap
rm -rf ./3rdparty/ccap/
rsync -av --exclude='tests' --exclude='examples' --exclude='scripts' --exclude='.*' "$XEGE_DIR/3rdparty/ccap/" ./3rdparty/ccap/

# 拷贝第三方 - libpng
rm -rf ./3rdparty/libpng/
rsync -av --exclude='.*' --exclude='*.png' --exclude='libtests' --exclude='examples' --exclude='pngsuite' --exclude='tests' "$XEGE_DIR/3rdparty/libpng/" ./3rdparty/libpng/

# 拷贝第三方 - zlib
rm -rf ./3rdparty/zlib/
rsync -av --exclude='.*' --exclude='doc' --exclude='examples' --exclude='test' --exclude='testzlib' --exclude='dotzlib' "$XEGE_DIR/3rdparty/zlib/" ./3rdparty/zlib/

du -h -d 2 | sort -h
