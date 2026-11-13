import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // KT序列化
    alias(libs.plugins.kotlin.serialization)
    // Room 数据库代码生成与结构导出
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// APP版本名称
val appVerName = "Beta-1.0.0"

// APP包名
val appPackageName = "com.winter.mypass"

// 读取本地签名配置
val signingProperties = Properties().apply {
    val signingFile = rootProject.file("signing.properties")
    if (signingFile.isFile) signingFile.reader(Charsets.UTF_8).use { load(it) }
}

android {
    namespace = appPackageName
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = appPackageName
        minSdk = 28
        targetSdk = 37
        versionCode = appVerCode()
        versionName = appVerName

        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += "arm64-v8a"
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("key") {
            storeFile = signingProperties.getProperty("storeFile")?.takeIf { it.isNotBlank() }?.let { rootProject.file(it) }
            storePassword = signingProperties.getProperty("storePassword")
            keyAlias = signingProperties.getProperty("keyAlias")
            keyPassword = signingProperties.getProperty("keyPassword")
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("key")
        }
        release {
            signingConfig = signingConfigs.getByName("key")
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

room {
    // 版本化保存业务数据库结构，方便后续迁移使用
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // 自带的一些Compose依赖
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Miuix 主界面组件、主题与扩展图标
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)

    // Compose 页面路由、回退栈与导航展示
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    // 页面生命周期、导航宿主和各栈项的 ViewModel
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    // 保存与恢复类型安全路由
    implementation(libs.kotlinx.serialization.core)

    // MMKV 高性能键值存储与对象 JSON 序列化
    implementation(libs.mmkv)
    implementation(libs.kotlinx.serialization.json)

    // Room 关系型存储、SQLCipher 整库加密与协程调度
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.sqlite)
    implementation(libs.sqlcipher)
    implementation(libs.kotlinx.coroutines.android)

    // 测试相关依赖
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

/**
 * 生成 yyMMddNN 格式的版本号，NN是当日git提交次数，最大99
 * @return 版本号
 */
fun appVerCode(): Int {
    val today = LocalDate.now()
    val git = providers.exec {
        workingDir(rootDir)
        commandLine(
            "git", "rev-list", "--count",
            "--since=${today}T00:00:00",
            "--before=${today.plusDays(1)}T00:00:00", "HEAD"
        )
        isIgnoreExitValue = true
    }
    val count = git.standardOutput.asText.get().trim().toIntOrNull()?.coerceAtMost(99) ?: 0
    return today.format(DateTimeFormatter.ofPattern("yyMMdd")).toInt() * 100 + count
}
