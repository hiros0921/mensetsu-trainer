// 外側。HTTP・WebSocket・DB・LLM 呼び出し。
//
// 依存の向きは app → domain の一方向だけ。domain は app を知らない。
// LLM のインターフェースは domain 側にあり、その実装がここに来る（依存性逆転）。

plugins {
  id("org.springframework.boot")
  id("io.spring.dependency-management")
}

dependencies {
  implementation(project(":domain"))

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
