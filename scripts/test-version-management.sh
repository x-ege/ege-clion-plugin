#!/bin/bash

cd "$(dirname "$0")/.."

# 测试脚本：验证版本管理功能

set -e

echo "================================"
echo "版本管理功能测试"
echo "================================"
echo ""

echo "📋 步骤 1: 构建 buildSrc"
./gradlew :buildSrc:build

echo ""
echo "✅ buildSrc 构建成功"
echo ""

echo "📋 步骤 2: 测试 checkClionVersion 任务"
if ./gradlew checkClionVersion; then
    echo "✅ 版本检查通过 - untilBuild 已是最新版本"
else
    echo "⚠️  版本检查失败 - 需要更新 untilBuild"
    echo ""
    echo "📋 步骤 3: 运行 updateUntilBuild 更新版本"
    ./gradlew updateUntilBuild
    echo ""
    echo "📋 步骤 4: 重新检查版本"
    ./gradlew checkClionVersion
fi

echo ""
echo "================================"
echo "✅ 所有测试通过！"
echo "================================"
