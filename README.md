# 名片集（安卓 App）

拍照收集名片，自动识别，导出 Excel。

- **离线识别**（默认）：用手机上的中文文字识别，不联网，免费；识别完会自动分类，需要核对。
- **Claude 识别**（可选）：在 App“设置”里填 Anthropic API Key，准确度更高，按用量计费。Key 只存在手机上。
- **导出 Excel**：保存到手机“下载”文件夹，或直接发到微信、邮件。
- 名片和照片都存在手机本地；卸载 App 会清空，请定期导出 Excel 备份。

## 方法一：用 GitHub 在线编译（不用装任何软件）

1. 登录 github.com，右上角 “+” → **New repository**，名字随便填（如 card-collector），选 **Private**，点 **Create repository**。
2. 在新仓库页面点 **uploading an existing file**，把解压后 `CardCollector` 文件夹里的**所有内容**拖进去，点 **Commit changes**。
3. 检查仓库里是否有 `.github/workflows/build.yml`。电脑可能隐藏了以点开头的文件夹，没有的话：**Add file → Create new file**，文件名填 `.github/workflows/build.yml`，把下面“编译流程”整段粘进去，再 Commit。
4. 打开仓库的 **Actions** 页，会自动开始编译（约 5–8 分钟）。变成绿色对勾后点进去，在页面底部 **Artifacts** 下载 `CardCollector-APK`，解压得到 `app-debug.apk`。
5. 把 APK 发到小米手机（微信文件传输助手、数据线都行），点开安装。小米会提示“未知来源”，按提示允许即可。

### 编译流程（.github/workflows/build.yml）
```yaml
name: 编译 APK

on:
  push:
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - name: 接受安卓许可
        run: yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null || true
      - name: 编译
        run: |
          chmod +x gradlew
          ./gradlew assembleDebug --no-daemon
      - uses: actions/upload-artifact@v4
        with:
          name: CardCollector-APK
          path: app/build/outputs/apk/debug/app-debug.apk
```

## 方法二：用 Android Studio

用 Android Studio 打开 `CardCollector` 文件夹，等同步完成，菜单 **Build → Build APK(s)**，生成的文件在 `app/build/outputs/apk/debug/app-debug.apk`。

## 说明

- 需要的权限：网络（仅 Claude 识别时使用）。拍照通过系统相机完成，App 本身不申请相机权限。
- 默认模型 `claude-sonnet-5-5`，可在“设置”里改。
- 包名 `com.virogin.cardcollector`，最低支持 Android 8.0。
