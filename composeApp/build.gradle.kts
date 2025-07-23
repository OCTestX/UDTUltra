import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    jvm("desktop")
    
    sourceSets {
        val desktopMain by getting
        
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation("io.github.octestx:basic-multiplatform-lib:0.1.4-DownVerB")
            implementation("io.github.octestx:basic-multiplatform-ui-lib:0.1.5")
            implementation("io.github.vinceglb:filekit-coil:0.10.0-beta01")
            implementation("io.github.vinceglb:filekit-core:0.10.0-beta01")
            implementation("io.github.vinceglb:filekit-dialogs:0.10.0-beta01")
            implementation("io.github.vinceglb:filekit-dialogs-compose:0.10.0-beta01")

        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}


compose.desktop {
    application {
        mainClass = "io.github.octest.udtultra.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "io.github.octest.udtultra"
            packageVersion = "1.0.0"
        }
    }
}
