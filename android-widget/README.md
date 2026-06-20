# NeoNoting Android ウィジェット

NeoNoting 本体（PWA）とは別の、ホーム画面ウィジェット用ネイティブ Android アプリです。
GAS プロキシの JSON API を直接叩いて、タスクをホーム画面に表示・完了トグルします。
**本体の `index.html` には一切手を入れていません。**

## できること

- ホーム画面にタスク一覧を表示（未完了が上、完了は下に薄字＋取り消し線）
- 行タップで完了 / 未完了をトグル（GAS の `update` を呼ぶ → 本体・Notion に反映）
- 更新ボタンで再取得
- ヘッダタップで NeoNoting 本体（PWA）を開く
- 設定画面で GAS URL を入力（本体の設定に入れているのと同じ URL）

## 仕組み

```
ホーム画面ウィジェット (このアプリ)
   └─ POST {action:'list'}            ──▶ GAS Web アプリ ──▶ Notion DB
   └─ POST {action:'update',props:{done}}
```

本体 PWA と同じ `gasCall` プロトコル（`POST` JSON → `{ok,data,error}`）を
`GasClient.kt` で再実装しています。GAS の 302 リダイレクトは手動で追います。

## ビルド方法

1. Android Studio（Hedgehog 以降推奨）で `android-widget/` フォルダを開く
2. Gradle Sync（初回はラッパー生成を許可。CLI なら `gradle wrapper` 後に `./gradlew assembleDebug`）
3. 実機 or エミュレータにインストール（`./gradlew installDebug`）

> このリポジトリには Gradle Wrapper の jar（バイナリ）を含めていません。
> Android Studio で開けば自動生成されます。CLI でやる場合はマシンに Gradle 8.7 を入れて
> `gradle wrapper` を一度実行してください。

## 使い方

1. インストール後、アプリ（NeoNoting）を一度開くか、ホーム画面長押し →
   ウィジェット → NeoNoting を配置
2. 配置時に開く設定画面で GAS URL を貼り付けて保存
   （本体の「設定」に入れているのと同じ `https://script.google.com/macros/s/.../exec`）
3. ウィジェットにタスクが並ぶ。タップで完了トグル、更新ボタンで再取得

## 設定の変更

URL を変えたいときはアプリ一覧の「NeoNoting」アイコンから設定画面を再度開けます。

## 注意

- `TaskWidgetProvider.APP_URL` はヘッダタップで開く本体 URL です。
  自分の GitHub Pages URL に合わせて変更してください（既定: `https://memotan.github.io/neonoting/`）。
- 自動更新は省電力のため約 30 分間隔（`updatePeriodMillis`）。即時反映は更新ボタンで。
