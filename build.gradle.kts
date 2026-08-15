// ルート。ここでは共通の設定だけを持ち、依存は各モジュールで宣言する。
//
// Spring Boot のプラグインは、ここでは apply しない（apply false）。
// ルートで適用すると domain にも降りてきてしまい、分割の意味が無くなる。

plugins {
  java
  id("org.springframework.boot") version "3.5.6" apply false
  id("io.spring.dependency-management") version "1.1.7" apply false
}

subprojects {
  apply(plugin = "java")

  group = "jp.lightech.mensetsu"
  version = "0.1.0-SNAPSHOT"

  repositories { mavenCentral() }

  extensions.configure<JavaPluginExtension> {
    toolchain {
      // 手元の JDK に依存させない。別の機体でも同じ結果になるようにする。
      languageVersion.set(JavaLanguageVersion.of(21))
    }
  }

  tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // sealed interface の switch 網羅性など、21 の検査を素通りさせない。
    options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
  }

  tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
      events("passed", "skipped", "failed")
      showStandardStreams = false
    }
  }
}
