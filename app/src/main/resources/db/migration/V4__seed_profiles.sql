-- 面接官の初期設定。
--
-- 【重要】実在の企業名・個人名を入れないこと（仕様書10章）。
-- ここに入っているのは役割の名前だけで、実在の人物ではない。

INSERT INTO interviewer_profiles
  (code, display_name, pressure_base, probe_depth, small_talk_ratio)
VALUES
  -- エンジニア面接。技術用語を3段掘る（仕様書4-1）。
  -- 圧の初期値は低い。掘るのは圧をかけるためではなく、確かめるため。
  ('engineer_standard', '技術面接官', 20, 3, 0),

  -- 圧迫面接。初期値から高い。回答内容で上下する（仕様書4-2）。
  ('pressure_hard',     '圧迫面接官', 55, 3, 0),

  -- 英語面接。制限時間と沈黙が主眼なので、掘りは浅く、圧も低い。
  -- 雑談を少し入れるのは、実際の英語面接が短い雑談から入るため。
  ('english_standard',  'English Interviewer', 20, 2, 20);
