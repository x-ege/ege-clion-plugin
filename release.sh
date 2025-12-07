#!/bin/bash

# 配置主干分支名称
MASTER_BRANCH="master"

# 1. 前置检查

# 1.1 检查当前分支是否为主干分支
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "$CURRENT_BRANCH" != "$MASTER_BRANCH" ]; then
    echo "错误：当前不在主干分支 ($MASTER_BRANCH)。当前分支是: $CURRENT_BRANCH"
    echo "请切换到 $MASTER_BRANCH 分支后再执行此脚本。"
    exit 1
fi

# 1.2 检查工作目录是否干净
if [ -n "$(git status --porcelain)" ]; then
    echo "错误：工作目录不干净 (Working directory is not clean)。"
    echo "请先提交 (commit) 或暂存 (stash) 您的更改。"
    exit 1
fi

# 1.3 同步远端 tag
echo "正在同步远端 tag..."
git fetch --tags
if [ $? -ne 0 ]; then
    echo "错误：无法 fetch 远端 tags，请检查网络或仓库权限。"
    exit 1
fi

# 2. 执行插件生成操作
echo "正在构建插件..."
./gradlew buildPlugin

# 检查构建是否成功
if [ $? -ne 0 ]; then
    echo "构建失败，脚本退出。"
    exit 1
fi

echo "构建成功。"

# 3. 获取版本号
# 从 gradle.properties 中提取 pluginVersion，并去除可能存在的空白字符
VERSION=$(grep "^pluginVersion=" gradle.properties | cut -d'=' -f2 | tr -d '[:space:]')

if [ -z "$VERSION" ]; then
    echo "错误：无法在 gradle.properties 中找到 pluginVersion。"
    exit 1
fi

echo "检测到版本号: $VERSION"

# 定义 Tag 名称，这里默认使用 v 前缀，例如 v1.1.2
TAG_NAME="v$VERSION"

# 4. 检查 tag 是否存在
if git rev-parse "$TAG_NAME" >/dev/null 2>&1; then
    echo "Tag '$TAG_NAME' 已经存在，跳过创建和推送。"
    echo "如果需要重新发布该版本，请先删除本地和远端的对应 tag，并更新版本号。"
else
    # 5. 创建 tag
    echo "正在创建 tag: $TAG_NAME"
    git tag "$TAG_NAME"
    
    if [ $? -ne 0 ]; then
        echo "创建 tag 失败。"
        exit 1
    fi

    # 6. 推送到远端
    echo "正在将 tag 推送到远端 (origin)..."
    git push origin "$TAG_NAME"
    
    if [ $? -ne 0 ]; then
        echo "推送 tag 失败。"
        # 如果推送失败，建议删除本地 tag 以免造成混淆
        git tag -d "$TAG_NAME"
        echo "已回滚本地 tag。"
        exit 1
    fi
    
    echo "成功创建并推送 tag: $TAG_NAME"
fi
