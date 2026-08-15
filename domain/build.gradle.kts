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

// 基準の案を、同じ面接に当てて比べる。
//
//   ./gradlew :domain:policycompare
//
// 案を文章で並べても選べない。同じ相手を各案で評価して、判定がどう変わるかを
// 見て初めて選べる。第5段階で諏訪さんが基準を決めるための道具。
tasks.register<JavaExec>("policycompare") {
  group = "verification"
  description = "スコアリングの案を同じ面接に当てて比べる（Spring も DB も LLM も使わない）"
  classpath = sourceSets["test"].runtimeClasspath
  mainClass.set("jp.lightech.mensetsu.domain.scoring.PolicyCompare")
}

// 圧の設定を、同じ回答パターンに当てて比べる。
//
//   ./gradlew :domain:pressuresweep
//
// 幅をいくつにすべきかは、文章で考えても決まらない。回答のパターンごとに
// 圧がどう動くかを並べて初めて選べる。第7段階で諏訪さんが決めるための道具。
tasks.register<JavaExec>("pressuresweep") {
  group = "verification"
  description = "圧の設定案を同じ回答パターンに当てて比べる"
  classpath = sourceSets["test"].runtimeClasspath
  mainClass.set("jp.lightech.mensetsu.domain.interview.PressureSweep")
}

// 圧迫面接モードの基準案を比べる。
//
//   ./gradlew :domain:pressurepolicy
tasks.register<JavaExec>("pressurepolicy") {
  group = "verification"
  description = "圧迫面接モードのスコアリング案を、圧の設定ごとに比べる"
  classpath = sourceSets["test"].runtimeClasspath
  mainClass.set("jp.lightech.mensetsu.domain.scoring.PressurePolicyCompare")
}
