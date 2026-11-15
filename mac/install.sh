#!/bin/sh
# 在目标 Mac 上运行：安装到 ~/Library/Application Support/MacRemote 并设为登录自启
set -e
cd "$(dirname "$0")"
DIR="$HOME/Library/Application Support/MacRemote"
PLIST="$HOME/Library/LaunchAgents/com.macremote.daemon.plist"
mkdir -p "$DIR" "$HOME/Library/LaunchAgents"
launchctl bootout gui/$(id -u)/com.macremote.daemon 2>/dev/null || true
cp macremoted "$DIR/macremoted"
chmod +x "$DIR/macremoted"
cat > "$PLIST" <<PL
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>Label</key><string>com.macremote.daemon</string>
  <key>ProgramArguments</key><array><string>$DIR/macremoted</string></array>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><true/>
  <key>ProcessType</key><string>Interactive</string>
  <key>StandardErrorPath</key><string>/tmp/macremoted.log</string>
</dict></plist>
PL
launchctl bootstrap gui/$(id -u) "$PLIST"
echo "已安装并启动。首次需在 系统设置 → 隐私与安全性 → 辅助功能 里允许 macremoted。"
