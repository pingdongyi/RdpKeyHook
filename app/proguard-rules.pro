# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# Xposed 入口类 (xposed_init 通过反射加载)
-keep class io.github.pingdongyi.rdpkeyhook.MainHook { *; }

# 保留本模块全部类，避免 R8 重命名/裁剪 Hook 回调
-keep class io.github.pingdongyi.rdpkeyhook.** { *; }

# 无论如何保留 XC_MethodHook 的 before/after 回调实现
-keepclassmembers class * extends de.robv.android.xposed.XC_MethodHook {
    protected void beforeHookedMethod(...);
    protected void afterHookedMethod(...);
}