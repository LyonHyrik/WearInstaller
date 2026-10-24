path = r'app\build.gradle.kts'
with open(path, encoding='utf-8') as f:
    s = f.read()

old = '''dependencies {
    // Wear Compose 1.6.0：TransformingLazyColumn / SurfaceTransformation 等新列表 API 已具备，
    // 且不需 AGP 9 / compileSdk 37？1.7.0 要求 AGP 9.1+ 兼 compileSdk 37，暂不升级。
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.wear.compose:compose-material3:1.6.0")
    implementation("androidx.wear.compose:compose-foundation:1.6.0")
    // 官方页面管理器：SwipeDismissableNavHost / rememberSwipeDismissableNavController。
    // 让右滑返回键回到上一页而不是直接退出应用。
    implementation("androidx.wear.compose:compose-navigation:1.6.0")
    // SwipeDismissableNavHost 的图构建使用标准 NavGraphBuilder DSL（composable(...)）。
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.wear.compose:compose-ui-tooling:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // 直连 adbd 实现 ADB 协议（无需 adb 可执行文件 / adb server）。
    implementation("dev.mobile:dadb:2.0.0")
}'''

new = '''dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.wear.compose:compose-material:1.4.1")
    implementation("androidx.wear.compose:compose-foundation:1.4.1")
    implementation("androidx.wear.compose:compose-navigation:1.4.1")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.wear.compose:compose-ui-tooling:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("dev.mobile:dadb:2.0.0")
}'''

if old not in s:
    print('MISSING')
else:
    s2 = s.replace(old, new)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(s2)
    print('OK')
