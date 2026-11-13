// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // 为导航路由生成序列化器
    alias(libs.plugins.kotlin.serialization) apply false
}