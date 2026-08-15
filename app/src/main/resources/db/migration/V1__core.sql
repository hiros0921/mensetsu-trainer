-- 面接の進行そのものを記録する3つの表。
--
-- 値の持ち方について
--
--   状態や種別は smallint ではなく text + CHECK にしてある。
--   数値にすると保存は小さいが、psql で見たときに 3 とだけ出て、
--   毎回いちいち定義を引かないと読めない。
--   このプロジェクトは「面談で見せて説明する」ことが目的なので、
--   その場で SELECT して意味が分かるほうが価値が高い。
--   行数は1セッションあたり数十行で、大きさは問題にならない。


-- ── 面接官の設定 ──
--
-- 面接官の性格を、コードではなくデータで持つ。
-- 「圧の強いエンジニア面接官」を後から足せるようにするため。
CREATE TABLE interviewer_profiles (
  id                bigserial PRIMARY KEY,
  code              text NOT NULL UNIQUE,   -- 'engineer_standard' など
  display_name      text NOT NULL,

  -- 圧の初期値。0〜100。圧迫モード以外でも動くが、初期値が低い。
  pressure_base     smallint NOT NULL DEFAULT 20
                      CHECK (pressure_base BETWEEN 0 AND 100),
  -- 技術用語を何段まで掘るか（仕様書4-1 は3段）。
  probe_depth       smallint NOT NULL DEFAULT 3
                      CHECK (probe_depth BETWEEN 1 AND 5),
  -- 雑談の量。0 で一切しない。
  small_talk_ratio  smallint NOT NULL DEFAULT 0
                      CHECK (small_talk_ratio BETWEEN 0 AND 100),

  created_at        timestamptz NOT NULL DEFAULT now()
);


-- ── 面接セッション1回分 ──
CREATE TABLE sessions (
  id                bigserial PRIMARY KEY,
  -- 外から見える識別子。WebSocket と画面で使う。
  -- 連番を URL に出すと、他人のセッションを総当たりで引ける。
  public_id         uuid NOT NULL UNIQUE DEFAULT gen_random_uuid(),

  mode              text NOT NULL
                      CHECK (mode IN ('ENGINEER', 'PRESSURE', 'ENGLISH')),
  interviewer_profile_id bigint NOT NULL REFERENCES interviewer_profiles(id),

  -- 【重要】今どのフェーズにいるか。サーバーが持つ唯一の正（仕様書3章）。
  -- クライアントから遷移を指示させないので、この列を書き換えてよいのは
  -- ドメイン層の遷移判定を通った経路だけ。
  current_phase     text NOT NULL DEFAULT 'INTRO'
                      CHECK (current_phase IN
                        ('INTRO','PROBE','PRESSURE','REVERSE','CLOSING','RESULT')),

  -- 進行状態。
  --
  -- 【重要】ABANDONED（中断）を必ず分けて持つ。
  --   RUNNING    進行中
  --   COMPLETED  RESULT まで到達した
  --   ABANDONED  RESULT に到達せずに終わった（ブラウザを閉じた等）
  --
  -- 切断からの復帰は作らない方針だが、中断したセッションが
  -- 履歴やスコアの集計に混ざると、平均点が意味を失う。
  -- 「答えるのをやめた面接」は、落ちた面接とは違う。
  -- 集計は必ず COMPLETED だけを見ること（下の completed_sessions を使う）。
  status            text NOT NULL DEFAULT 'RUNNING'
                      CHECK (status IN ('RUNNING','COMPLETED','ABANDONED')),

  -- 現在の圧。0〜100。圧迫モード以外でも動く（表情に使うため。仕様書6章）。
  pressure          smallint NOT NULL DEFAULT 20
                      CHECK (pressure BETWEEN 0 AND 100),

  -- どの実装で動かしたか。スタブで作ったデータと本物を後から区別する。
  -- これが無いと、第3段階の動作確認データが実績の数字に混ざる。
  engine_kind       text NOT NULL DEFAULT 'STUB'
                      CHECK (engine_kind IN ('STUB','CLAUDE')),

  started_at        timestamptz NOT NULL DEFAULT now(),
  -- 最後にクライアントから何か届いた時刻。中断の判定に使う。
  last_seen_at      timestamptz NOT NULL DEFAULT now(),
  ended_at          timestamptz
);

-- 進行中のセッションを拾う。中断の掃除で使う。
CREATE INDEX sessions_running_idx ON sessions (last_seen_at)
  WHERE status = 'RUNNING';

-- 【重要】履歴とスコア集計は、必ずこのビューを通すこと。
-- sessions を直接数えると中断が混ざる。
CREATE VIEW completed_sessions AS
  SELECT * FROM sessions WHERE status = 'COMPLETED';


-- ── フェーズの遷移履歴 ──
--
-- 仕様書9章の要件「なぜ遷移したかを残す」。
-- あとから「なぜこの評価になったか」を追えるようにするため。
-- 理由を後付けで書く作りにすると、必ず書き忘れる。
-- ドメイン層の遷移オブジェクトが理由を持ち、それをそのまま入れる。
CREATE TABLE session_phases (
  id                bigserial PRIMARY KEY,
  session_id        bigint NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,

  phase             text NOT NULL
                      CHECK (phase IN
                        ('INTRO','PROBE','PRESSURE','REVERSE','CLOSING','RESULT')),
  -- このフェーズが何番目か。1 から。
  seq               integer NOT NULL,

  entered_at        timestamptz NOT NULL DEFAULT now(),
  exited_at         timestamptz,
  -- このフェーズで何往復したか。打ち切り条件の検証に使う。
  round_count       smallint NOT NULL DEFAULT 0,

  -- なぜこのフェーズに入ったか。種別と、その具体。
  --   ROUND_LIMIT       上限ラウンドに達した
  --   TOPIC_EXHAUSTED   掘る対象が尽きた
  --   PRESSURE_MAX      圧が上限に達した（強制遷移。仕様書4-2）
  --   DEPTH_FAILED      規定の段数まで答え切れなかった（仕様書4-1）
  --   SURVIVED          規定ラウンド耐え切った
  --   ANSWERED          単に回答を受け取った（INTRO・REVERSE）
  --   START             セッションの開始
  entered_reason    text NOT NULL,
  -- 人が読む説明。「3段目で答えが出なかった（React の選定理由）」など。
  entered_detail    text NOT NULL DEFAULT '',

  UNIQUE (session_id, seq)
);

CREATE INDEX session_phases_session_idx ON session_phases (session_id, seq);


-- ── 発言1往復 ──
CREATE TABLE turns (
  id                bigserial PRIMARY KEY,
  session_id        bigint NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  session_phase_id  bigint REFERENCES session_phases(id) ON DELETE SET NULL,

  phase             text NOT NULL,
  -- セッション内の通し番号。1 から。
  turn_no           integer NOT NULL,

  -- 面接官の発言。
  question_text     text NOT NULL,

  -- 【重要】面接官の発言は2種類ある。ここを分けて持つ。
  --   CANNED     相槌・つなぎ。アプリ側の定型。LLM を呼ばない
  --   GENERATED  深掘り・矛盾の指摘。LLM が作る
  --
  -- 分ける理由は速度。相槌は回答を受け取った瞬間に返せる。
  -- 実際の面接官も、次を考えている間はこれをやっている。
  -- 深掘りはその裏で作る。待ち時間が「間」として自然に見える。
  --
  -- 記録として残す理由は、あとで「どれだけ LLM に頼ったか」を
  -- 数えられるようにするため。相槌ばかりでは面接にならない。
  question_kind     text NOT NULL DEFAULT 'GENERATED'
                      CHECK (question_kind IN ('CANNED','GENERATED')),

  -- 利用者の回答。まだ答えていなければ NULL。
  answer_text       text,

  -- 【重要】入力方式（仕様書8章④・完成条件）。
  -- 音声でもテキストでも、サーバーが受け取るのは文字列だけ。
  -- 違うのはこの列の値だけにする。そうすれば、
  -- 音声が使えない環境にフォールバックしても後段が何も変わらない。
  input_method      text
                      CHECK (input_method IN ('TEXT','VOICE')),

  -- 質問を出してから回答が確定するまで。
  elapsed_ms        integer,
  -- 入力（発話）が止まっていた時間。英語面接の打ち切りとスコアに使う。
  -- 【重要】これはサーバーが計る（仕様書5章161行）。
  -- クライアントから送られた値をそのまま入れないこと。
  silence_ms        integer,
  -- 制限時間切れで打ち切られたか。
  timed_out         boolean NOT NULL DEFAULT false,

  asked_at          timestamptz NOT NULL DEFAULT now(),
  answered_at       timestamptz,

  UNIQUE (session_id, turn_no)
);

CREATE INDEX turns_session_idx ON turns (session_id, turn_no);
