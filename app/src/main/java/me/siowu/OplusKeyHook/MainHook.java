package me.siowu.OplusKeyHook;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import me.siowu.OplusKeyHook.hooks.RdpKeyboardHook;

/**
 * 模块入口：只做一件事——让远程桌面类应用的硬件键盘事件不被系统拦截。
 */
public class MainHook implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        new RdpKeyboardHook().handleLoadPackage(lpparam);
    }
}
