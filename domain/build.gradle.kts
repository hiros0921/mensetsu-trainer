// ドメイン層。面接の進行と評価そのもの。
//
// 【重要】ここに依存を足すときは、必ず立ち止まること。
//
// このモジュールの価値は「何にも依存していないこと」にある。
// Spring も、DB のドライバも、HTTP のクライアントも、JSON のライブラリも入れない。
// 入れた瞬間に、単体テストに準備が要るようになり、
// 「フレームワークなしでステートマシンが動く」という主張が崩れる。
//
// 依存してよいのは、テスト用の JUnit だけ。

// 面接を1回通して、やりとりを表示する。
//
//   ./gradlew :domain:walkthrough
//
// テストは通ったかどうかしか教えてくれない。どんな面接になっているかは、
// 実際に並べて読まないと判断できない。第6段階以降の確認にも使う。
tasks.register<JavaExec>("walkthrough") {
  group = "verification"
  description = "スタブだけで面接を通し、やりとりを表示する（Spring も DB も LLM も使わない）"
  classpath = sourceSets["test"].runtimeClasspath
  mainClass.set("jp.lightech.mensetsu.domain.Walkthrough")
}

dependencies {
  testImplementation(platform("org.junit:junit-bom:5.11.4"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation("org.junit.jupiter:junit-jupiter-params")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
