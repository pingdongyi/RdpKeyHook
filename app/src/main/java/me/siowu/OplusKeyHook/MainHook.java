package me.siowu.OplusKeyHook;


import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import me.siowu.OplusKeyHook.hooks.KeyHook;
import me.siowu.OplusKeyHook.hooks.RdpKeyboardHook;
import me.siowu.OplusKeyHook.hooks.ShortcutsHook;
import me.siowu.OplusKeyHook.hooks.LauncherHook;

public class MainHook implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        new LauncherHook().handleLoadPackage(lpparam);
        new KeyHook().handleLoadPackage(lpparam);
        new RdpKeyboardHook().handleLoadPackage(lpparam);
        new ShortcutsHook().handleLoadPackage(lpparam);
//            暂时只启用捕获点开一键指令之后添加到桌面
//            new ShortcutsCardHook().handleLoadPackage(lpparam);
    }
}

