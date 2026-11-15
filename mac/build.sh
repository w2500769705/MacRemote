#!/bin/sh
# 编译成 x86_64 可执行文件（老 Intel Mac 用）
cd "$(dirname "$0")"
clang -arch x86_64 -mmacosx-version-min=10.13 -fobjc-arc -O2 \
  -framework Foundation -framework AppKit -framework ApplicationServices \
  -o macremoted macremoted.m
codesign -f -s - --identifier com.macremote.daemon macremoted
