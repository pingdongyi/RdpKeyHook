<div align="center">

# 远程桌面键盘直通

**让远程桌面类应用（Windows App / Microsoft 远程桌面 等）在前台时可以使用完整的硬件键盘快捷键**

</div>

---

## ✨ 功能

在 Android / ColorOS 上使用外接键盘连接远程桌面时，一部分 Windows 快捷键会被系统层拦截，
根本不会转发到远端 Windows，例如：

| 按键 | Android 系统默认行为 |
| ---- | -------------------- |
| 单独按 `Win`(⊞ / Meta) | 打开最近任务 |
| `Alt + Tab` | 打开最近任务 |
| `Win + Space` / `Alt + Shift` | 切换输入法 / 键盘布局 |
| `Home` / `AppSwitch` | 回桌面 / 最近任务 |
| ColorOS 自定义多媒体键盘按键 | 打开全局搜索等 |

本模块在系统框架进程中 Hook 输入分发链路，当**远程桌面应用位于前台**时直接放行按键，
让远端 Windows 收到完整的组合键；其它应用不受影响，电源键与音量键保持系统行为。

> 无界面、无额外功耗，只在目标应用前台时生效。

> 模块包名：`io.github.pingdongyi.rdpkeyhook`

## 📦 支持的应用

默认白名单（可在 `RdpKeyboardHook#TARGET_PACKAGES` 中增删）：

- `com.microsoft.rdc.androidx`（Windows App / 新版 Microsoft 远程桌面）
- `com.microsoft.rdc.android`（旧版 Microsoft Remote Desktop）
- `com.microsoft.rdc.android.beta`
- `de.freerdp.afreerdp`、`com.freerdp.afreerdp`（FreeRDP）
- `com.realvnc.viewer.android`（RealVNC）

## 🚀 使用

1. 设备已安装 Xposed / LSPosed 环境；
2. 将本模块作用域勾选为「系统框架（android）」；
3. 首次激活重启一次手机；
4. 打开远程桌面应用，外接键盘即可正常使用 `Win`、`Alt+Tab`、`Win+Space` 等快捷键。

## 🔧 实现原理

```
InputDispatcher (native)
      │
      ▼
com.android.server.wm.InputManagerCallback      ← Hook：最可靠的单点
      │  interceptKeyBeforeQueueing / interceptKeyBeforeDispatching
      ▼
com.android.server.policy.PhoneWindowManager    ← Hook：兜底 + 键盘布局切换
      ▼
前台应用（com.microsoft.rdc.androidx）
```

前台应用命中白名单时：

- `interceptKeyBeforeQueueing` 返回 `ACTION_PASS_TO_USER`（放行给应用）
- `interceptKeyBeforeDispatching` 返回 `0`（立即分发）
- `dispatchUnhandledKey` 返回 `null`（不做系统兜底）
- `handleSwitchKeyboardLayout` / `sendSwitchKeyboardLayout` 直接短路

前台包名优先读取 `PhoneWindowManager.mDefaultDisplayPolicy.mFocusedApp`（无锁、开销极小），
失败时回退到 `ActivityTaskManager.getFocusedRootTaskInfo()`。

更详细的说明见 [docs/远程桌面键盘直通说明.md](docs/远程桌面键盘直通说明.md)。

## 🏗️ 构建

本地构建（Android Studio 或命令行）：

```bash
./gradlew assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`。

仓库自带 GitHub Actions：push 到 `main` 会编译并上传 Artifact；push `v*` 标签会自动创建
Release 并附带 APK。例如：

```bash
git tag -a v1.9 -m "v1.9"
git push origin v1.9
```

## 📄 致谢

- 项目结构与 Xposed 脚手架参考自 [siowu/OplusKeyHook](https://github.com/siowu/OplusKeyHook)。
- 本模块仅保留「远程桌面键盘直通」功能。

## 🛡️ 免责声明

本模块仅供学习与技术研究使用，请勿用于任何违反法律法规的用途。作者不对使用本模块造成的任何后果承担责任。
