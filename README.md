# Mac 遥控（MacRemote）

用安卓手机遥控 Mac，支持按键遥控、触控板、音量调节和局域网自动发现，适合连接电视的 Mac 使用。

当前 Android 版本：0.2.3（versionCode 5）。Mac 接收端的构建脚本面向 Intel x86_64，最低部署目标为 macOS 10.13；其他架构尚未验证。

## 结构
- `mac/` Mac 端 `macremoted`（Objective-C，x86_64，最低 macOS 10.13）
  - `build.sh` 编译+ad-hoc 签名；`install.sh` 在目标 Mac 上装到 `~/Library/Application Support/MacRemote/` 并用 LaunchAgent 登录自启
  - 日志 `/tmp/macremoted.log`；需在 辅助功能 里允许 macremoted
- `android/` 安卓 App「Mac 遥控」（Compose，minSdk 26）

## 协议
UDP 47800，一包一条文本指令，见 `mac/macremoted.m` 文件头注释。手机广播 `ping` 自动发现 Mac。

## 快捷键配置
`android/app/src/main/java/com/macremote/app/Profiles.kt`：Jellyfin（下一集 shift+N）、B站（]）、其他网站。

## 使用范围

当前协议没有配对、身份认证或加密。接收端监听所有 IPv4 网络接口的 UDP 47800 端口，能访问该端口的设备可发送键鼠控制指令。请仅在可信的家庭局域网使用，不要将此端口转发到公网，也不要在公共 Wi-Fi 上运行接收端。

## 构建和安装

### Mac 接收端

需要 Xcode Command Line Tools。在目标 Intel Mac 上执行：

```sh
cd mac
sh build.sh
sh install.sh
```

然后在系统设置的「隐私与安全性 → 辅助功能」中允许 `macremoted`。安装脚本会设置当前用户登录自启；控制功能依赖此权限。

### Android App

需要 Android SDK 37 和兼容项目 Gradle/Android Gradle Plugin 的 JDK；项目 Java 编译目标为 17。用 Android Studio 打开 `android/`，或执行：

```sh
cd android
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

手机与 Mac 连接到同一局域网，在 App 中自动发现设备。访客网络隔离或防火墙可能影响发现和连接。

正式版执行 `./gradlew assembleRelease`。如需签名，在 `android/keystore.properties` 中填写自己的签名配置（该文件不提交）：

```properties
storeFile=/absolute/path/to/your-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

没有签名配置时 release 产物为未签名 APK。正式版输出位于 `android/app/build/outputs/apk/release/`。

## 许可证

项目自有代码使用 [MIT License](LICENSE)。第三方依赖遵循各自的许可证。
