// 外側。HTTP・WebSocket・DB・LLM 呼び出し。
//
// 依存の向きは app → domain の一方向だけ。domain は app を知らない。
// LLM のインターフェースは domain 側にあり、その実装がここに来る（依存性逆転）。

plugins {
  id("org.springframework.boot")
  id("io.spring.dependency-management")
}

// 本物の Claude API を相手に面接を1回通し、応答時間を測る。
//
//   ./gradlew :app:llmcheck
//   ./gradlew :app:llmcheck --args="PRESSURE adaptive"
//
// 【重要】これは課金される。
//
// .env をここで読んで、子プロセスの環境変数に渡す。Gradle も Spring も
// .env を自動では読まない。値はログに出さない（仕様書10章）。
tasks.register<JavaExec>("llmcheck") {
  group = "verification"
  description = "本物の Claude API で面接を1回通し、初文字までの時間を測る（課金あり）"
  classpath = sourceSets["test"].runtimeClasspath
  mainClass.set("jp.lightech.mensetsu.app.LlmCheck")
  doFirst {
    val env = rootProject.file(".env")
    if (env.exists()) {
      env.readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") && it.contains("=") }
        .forEach { line ->
          val i = line.indexOf('=')
          val key = line.substring(0, i).trim()
          // 【重要】シェルで指定した値を .env で上書きしない。
          //
          // 一度これで間違えた。MENSETSU_LLM_MODEL=claude-opus-5 を付けて実行したのに、
          // .env の claude-sonnet-5 が勝ち、別のモデルの数字を opus-5 の数字として
          // 報告しかけた。エラーは出ない。出力のモデル名を見て初めて気づいた。
          //
          // .env は「指定が無いときの既定」。指定があるならそちらを尊重する。
          if (System.getenv(key) == null) {
            environment(key, line.substring(i + 1).trim())
          }
        }
    }
  }
}

dependencies {
  implementation(project(":domain"))

  // Claude API の公式 SDK。
  //
  // 【重要】これは app 側にだけ入れる。domain に入れてはいけない。
  // ステートマシンが HTTP クライアントに依存すると、
  // 「フレームワークなしで単体テストできる」が成立しなくなる。
  // domain/src/test の DomainIsolationTest が、その番人になっている。
  implementation("com.anthropic:anthropic-java:2.54.0")

  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-websocket")
  implementation("org.springframework.boot:spring-boot-starter-jdbc")
  implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

  // マイグレーションは Flyway。SQL をそのまま書けるほうがよい。
  // JSONB や部分インデックスを使うので、ORM に隠されると困る。
  implementation("org.flywaydb:flyway-core")
  implementation("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("org.postgresql:postgresql")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
