package io.github.pingdongyi.rdpkeyhook.hooks;

import android.content.ComponentName;
import android.os.SystemClock;
import android.view.KeyEvent;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 远程桌面类应用（Microsoft 远程桌面 / Windows App / FreeRDP 等）硬件键盘直通。
 *
 * <p>Android / ColorOS 会在系统层拦截一批“全局快捷键”，导致这些按键根本不会交给前台
 * 应用，从而无法转发到远端 Windows，例如：
 * <ul>
 *     <li>单独按下 Win(Meta) 键      -> 打开最近任务</li>
 *     <li>Alt + Tab                  -> 打开最近任务</li>
 *     <li>Win + Space / Alt + Shift  -> 切换输入法、切换键盘布局</li>
 *     <li>Home / AppSwitch / 部分 ColorOS 自定义键</li>
 * </ul>
 *
 * <p>本 Hook 运行在系统框架（android 进程）中，在输入分发前的桥接类
 * {@code com.android.server.wm.InputManagerCallback} 以及策略类
 * {@code com.android.server.policy.PhoneWindowManager} 上做手脚：
 * 当远程桌面应用位于前台时，直接放行按键（{@code ACTION_PASS_TO_USER} / 返回 0），
 * 让 Windows App 能收到完整的按键组合；其余应用不受影响。
 *
 * <p>注意：电源键与音量键保持系统行为，避免影响关机、调节音量等基础操作。
 */
public class RdpKeyboardHook {

    private static final String TAG = "RdpKeyHook";
    private static final boolean DEBUG = true;

    /** WindowManagerPolicyConstants.ACTION_PASS_TO_USER */
    private static final int ACTION_PASS_TO_USER = 0x00000001;

    /** 需要键盘直通的远程桌面 / 远程控制类应用。 */
    private static final String[] TARGET_PACKAGES = {
            "com.microsoft.rdc.androidx",   // Windows App（新版 Microsoft 远程桌面）
            "com.microsoft.rdc.android",    // 旧版 Microsoft Remote Desktop
            "com.microsoft.rdc.android.beta",
            "de.freerdp.afreerdp",          // FreeRDP
            "com.freerdp.afreerdp",
            "com.realvnc.viewer.android",   // RealVNC
    };

    /** 前台包名缓存，避免每个按键事件都去解析一次。 */
    private static volatile String sFocusedPkg = "";
    private static volatile long sFocusedPkgTime = 0L;
    private static final long CACHE_MS = 300L;

    /** 系统进程里的 WindowManagerPolicy 引用，用于快速读取当前前台应用。 */
    private static volatile Object sPolicy;

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"android".equals(lpparam.packageName)) {
            return;
        }

        boolean ok = false;

        // 1. 输入分发桥接类：最可靠的单点，覆盖所有策略拦截
        ok |= hookInputManagerCallback(lpparam);

        // 2. 系统策略类：作为兜底（InputManagerCallback 通常会先命中）
        ok |= hookPolicy(lpparam, "com.android.server.policy.PhoneWindowManager");
        ok |= hookPolicy(lpparam, "com.android.server.policy.SubPhoneWindowManager");

        // 3. ColorOS 扩展实现，避免厂商在 overrideXXX 中再次拦截
        ok |= hookPolicyExt(lpparam, "com.android.server.policy.PhoneWindowManagerExtImpl");

        if (DEBUG) {
            XposedBridge.log(TAG + ": handleLoadPackage done, installed=" + ok);
        }
    }

    // ------------------------------------------------------------------
    // Hook 安装
    // ------------------------------------------------------------------

    private boolean hookInputManagerCallback(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> clazz = findClassOrNull("com.android.server.wm.InputManagerCallback", lpparam.classLoader);
        if (clazz == null) {
            log("InputManagerCallback not found");
            return false;
        }

        boolean ok = false;

        // 分发前拦截：返回 0 = 立即分发给应用
        ok |= hookMethod(clazz, "interceptKeyBeforeDispatching",
                new Class[]{android.os.IBinder.class, KeyEvent.class, int.class});

        // 入队前拦截：返回 ACTION_PASS_TO_USER = 放行给应用
        ok |= hookMethod(clazz, "interceptKeyBeforeQueueing",
                new Class[]{KeyEvent.class, int.class});

        // 未处理按键的兜底：返回 null 丢弃系统兜底动作
        ok |= hookMethod(clazz, "dispatchUnhandledKey",
                new Class[]{android.os.IBinder.class, KeyEvent.class, int.class});

        return ok;
    }

    private boolean hookPolicy(XC_LoadPackage.LoadPackageParam lpparam, String className) {
        Class<?> clazz = findClassOrNull(className, lpparam.classLoader);
        if (clazz == null) {
            return false;
        }

        boolean ok = false;
        ok |= hookMethod(clazz, "interceptKeyBeforeDispatching",
                new Class[]{android.os.IBinder.class, KeyEvent.class, int.class});
        ok |= hookMethod(clazz, "interceptKeyBeforeQueueing",
                new Class[]{KeyEvent.class, int.class});
        ok |= hookMethod(clazz, "dispatchUnhandledKey",
                new Class[]{android.os.IBinder.class, KeyEvent.class, int.class});

        // 硬件键盘布局切换（Win+Space / Alt+Shift 等，可能由 native 直接触发）
        ok |= hookMethod(clazz, "handleSwitchKeyboardLayout",
                new Class[]{int.class, int.class});
        // ColorOS/AOSP 中根据按键触发布局切换
        ok |= hookMethod(clazz, "sendSwitchKeyboardLayout",
                new Class[]{KeyEvent.class, int.class});

        return ok;
    }

    private boolean hookPolicyExt(XC_LoadPackage.LoadPackageParam lpparam, String className) {
        Class<?> clazz = findClassOrNull(className, lpparam.classLoader);
        if (clazz == null) {
            return false;
        }

        boolean ok = false;
        ok |= hookMethod(clazz, "overrideInterceptKeyBeforeDispatching",
                new Class[]{android.os.IBinder.class, KeyEvent.class, int.class});
        ok |= hookMethod(clazz, "overrideInterceptKeyBeforeQueueing",
                new Class[]{KeyEvent.class, int.class});
        return ok;
    }

    private boolean hookMethod(Class<?> clazz, String methodName, Class<?>[] params) {
        try {
            XC_MethodHook callback = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!handleMethodCall(param)) {
                        return;
                    }
                    // interceptKeyBeforeDispatching -> 0（立即分发）
                    // interceptKeyBeforeQueueing   -> ACTION_PASS_TO_USER
                    // dispatchUnhandledKey         -> null（不做系统兜底）
                    // switchKeyboardLayout 等      -> null（不切换）
                    String name = getMethodName(param);
                    if (name == null) {
                        return;
                    }
                    if (name.equals("interceptKeyBeforeDispatching")
                            || name.equals("overrideInterceptKeyBeforeDispatching")) {
                        param.setResult(0L);
                    } else if (name.equals("interceptKeyBeforeQueueing")
                            || name.equals("overrideInterceptKeyBeforeQueueing")) {
                        param.setResult(ACTION_PASS_TO_USER);
                    } else {
                        // dispatchUnhandledKey / handleSwitchKeyboardLayout / sendSwitchKeyboardLayout
                        param.setResult(null);
                    }
                }
            };

            Object[] typesAndCallback = new Object[params.length + 1];
            System.arraycopy(params, 0, typesAndCallback, 0, params.length);
            typesAndCallback[params.length] = callback;

            XposedHelpers.findAndHookMethod(clazz, methodName, typesAndCallback);
            if (DEBUG) {
                log("hooked " + clazz.getSimpleName() + "#" + methodName);
            }
            return true;
        } catch (Throwable t) {
            log("hook " + clazz.getSimpleName() + "#" + methodName + " failed: " + t);
            return false;
        }
    }

    /** 返回 true 表示命中目标应用，需要放行该按键。 */
    private boolean handleMethodCall(XC_MethodHook.MethodHookParam param) {
        String name = getMethodName(param);
        if (name == null) {
            return false;
        }

        KeyEvent event = findKeyEvent(param.args);
        boolean isQueueing = name.contains("interceptKeyBeforeQueueing");
        boolean isLayoutSwitch = name.contains("SwitchKeyboardLayout");
        boolean isDispatching = name.contains("interceptKeyBeforeDispatching")
                || name.contains("dispatchUnhandledKey");

        // 布局切换方法不带 KeyEvent，用缓存判断即可
        if (event != null && isQueueing && isProtectedKey(event.getKeyCode())) {
            return false;
        }

        if (!isTargetForeground(event, param.thisObject)) {
            return false;
        }

        if (DEBUG && (isDispatching || isQueueing || isLayoutSwitch)) {
            log("pass-through " + name + " key=" + (event != null ? event.getKeyCode() : -1)
                    + " focused=" + sFocusedPkg);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private static String getMethodName(XC_MethodHook.MethodHookParam param) {
        try {
            return param.method.getName();
        } catch (Throwable t) {
            return null;
        }
    }

    private static KeyEvent findKeyEvent(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof KeyEvent) {
                return (KeyEvent) arg;
            }
        }
        return null;
    }

    private static boolean isProtectedKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                return true;
            default:
                return false;
        }
    }

    private static boolean isTargetPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return false;
        }
        for (String target : TARGET_PACKAGES) {
            if (target.equals(pkg)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTargetForeground(KeyEvent event, Object hookObject) {
        long now = SystemClock.uptimeMillis();
        boolean needRefresh = (event != null
                && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0)
                || (now - sFocusedPkgTime) > CACHE_MS;
        if (needRefresh) {
            refreshFocused(hookObject);
        }
        return isTargetPackage(sFocusedPkg);
    }

    private static void refreshFocused(Object hookObject) {
        sFocusedPkgTime = SystemClock.uptimeMillis();
        captureSystemObjects(hookObject);

        String pkg = null;

        // 1) 最轻量：PhoneWindowManager.mDefaultDisplayPolicy.mFocusedApp
        try {
            Object policy = sPolicy;
            if (policy != null) {
                Object displayPolicy = XposedHelpers.getObjectField(policy, "mDefaultDisplayPolicy");
                if (displayPolicy != null) {
                    Object focusedApp = XposedHelpers.getObjectField(displayPolicy, "mFocusedApp");
                    if (focusedApp instanceof String && !((String) focusedApp).isEmpty()) {
                        pkg = (String) focusedApp;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // 2) 兜底：ActivityTaskManager.getFocusedRootTaskInfo().topActivity
        if (pkg == null || pkg.isEmpty()) {
            try {
                Object atm = XposedHelpers.callStaticMethod(
                        XposedHelpers.findClass("android.app.ActivityTaskManager", null), "getService");
                if (atm != null) {
                    Object info = null;
                    try {
                        info = XposedHelpers.callMethod(atm, "getFocusedRootTaskInfo");
                    } catch (Throwable t) {
                        info = XposedHelpers.callMethod(atm, "getFocusedStackInfo");
                    }
                    if (info != null) {
                        ComponentName cn = (ComponentName) XposedHelpers.getObjectField(info, "topActivity");
                        if (cn != null) {
                            pkg = cn.getPackageName();
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        sFocusedPkg = (pkg == null) ? "" : pkg;
    }

    /** 从 InputManagerCallback 拿到 WindowManagerService 与 WindowManagerPolicy 的引用。 */
    private static void captureSystemObjects(Object hookObject) {
        if (hookObject == null) {
            return;
        }
        try {
            String cls = hookObject.getClass().getName();
            if (cls.endsWith("InputManagerCallback")) {
                Object wms = XposedHelpers.getObjectField(hookObject, "mService");
                if (wms != null) {
                    Object policy = XposedHelpers.getObjectField(wms, "mPolicy");
                    if (policy != null) {
                        sPolicy = policy;
                    }
                }
            } else if (cls.equals("com.android.server.policy.PhoneWindowManager")
                    || cls.equals("com.android.server.policy.SubPhoneWindowManager")) {
                sPolicy = hookObject;
            }
        } catch (Throwable ignored) {
        }
    }

    private static Class<?> findClassOrNull(String name, ClassLoader classLoader) {
        try {
            return XposedHelpers.findClass(name, classLoader);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void log(String msg) {
        if (DEBUG) {
            XposedBridge.log(TAG + ": " + msg);
        }
    }
}
