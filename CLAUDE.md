# CLAUDE.md

## プロジェクト概要

**NeoNoting** — GitHub Pages 上で動くモバイルファーストの PWA。タスクとメモを管理し、
Google Apps Script (GAS) のプロキシ経由で Notion データベースに読み書きする。
同じ `index.html` を、PC ブラウザ（2ペイン）・スマホ・Capacitor 製 Android アプリが共用する。

- フロントエンドは **単一の HTML ファイル**（インライン CSS + バニラ JS、ビルド工程なし）
- バックエンドは GAS の Web アプリ（このリポジトリには**含まれない**。Notion トークンは GAS 側に保持）
- 依存は CDN のみ: Tabler Icons、Google Fonts の Doto（ロゴ用ドット書体）
- テスト・lint・ビルドの仕組みはない。ブラウザで動作確認する

## ファイル構成

| パス | 役割 |
|---|---|
| `index.html` | **本体**。GitHub Pages が配信する唯一の実装。タスク・メモ・設定・PC/スマホ両レイアウトをすべて含む |
| `manifest.json`, `icons/` | PWA マニフェスト（`start_url`/`scope` は `./`）とアイコン |
| `capacitor-app/` | PWA を Android アプリ化する Capacitor プロジェクト（下記） |
| `desktop-widget/` | Windows 用の常駐ウィジェット（Electron）。追跡対象は `main.js` と `widget.html` のみ |
| `android-widget/` | Android ホーム画面ウィジェット（Kotlin / Android Studio）。**git 未追跡** |
| `archive/` | 退避用コピー。**git 未追跡**・参照しない |

`node_modules/`、`capacitor-app/android/`（ネイティブ側の生成物）、`.gradle/` は追跡しない。

## アーキテクチャ

```
ブラウザ/アプリ (index.html) ──POST JSON──▶ GAS Web アプリ ──API──▶ Notion DB
 desktop-widget (Electron)  ──┘               (Notion トークンを保持)
 android-widget (Kotlin)    ──┘
```

### GAS 通信プロトコル

`gasCall(body)` 経由で `POST`（JSON）。レスポンスは `{ ok, data, error }` で、`ok=false` なら `error` を投げる。

- **タスク**: `list` / `create` / `update` / `delete` / `reorder`
- **メモ**: `memo_list` / `memo_get` / `memo_create` / `memo_update` / `memo_delete`

データ形状:
- タスク: `{ id, name, done, tags: [{name, color}], order, reminderAt }`
- メモ: `{ id, name, lines: [...] }` — 行は `{ type, text }` に加えて型ごとに追加フィールドを持つ
  - `text` / `numbered` / `bullet`: `text` のみ
  - `counter`: `text`（ラベル）+ `value`
  - `checklist`: `text` + `checked`
- 太字は行テキスト内の `**太字**`（保存はこのマークダウン風記法のまま）

### 状態と永続化

- localStorage: `nt_gas_url`（GAS URL）、`nt_custom_tags`、`nt_memo_cache`（メモ一覧キャッシュ）、`nt_memo_order`（メモ並び順）。Notion トークンはブラウザに置かない
- 操作は**楽観的更新**: UI を即時更新 → GAS へ反映 → 失敗時は同期ドット（`.sdot`）を `err` に
- ディープリンク: `#memo=ID` / `?memo=ID` と、Capacitor アプリの `neonoting://memo?id=ID`（`setupCapacitorDeepLink`）で該当メモを直接開く

## UI とレイアウト

- **スマホ（幅 <900px）**: 1カラム、`#app` は `max-width:480px`。Memo / Tasks のタブ切替とスワイプ切替。画面遷移は `#mainScreen` の `display` をインラインで切り替える方式
- **PC（幅 ≥900px）**: Tasks | Memo の2ペイン（CSS Grid、最大幅 1400px）
  - `#mainScreen` を `display:contents` にして子要素を `#app` のグリッドに載せている
  - スマホ向け JS はインライン `style.display` で表示を切り替えるため、PC 幅では `@media` 内の `!important` で上書きしている。**スマホ側の JS を変えずに PC を成立させるための意図的な作り**
  - メモを開くと右ペインが編集画面に入れ替わる（`#app.editing-memo` で一覧を隠す）
  - 設定・編集シートは中央のモーダル/ダイアログになる。Esc で閉じる、Ctrl(Cmd)+S でメモ保存
  - ブレークポイントは CSS の `@media (min-width:900px)` と JS の `wideQuery` の**両方**にある。変えるときは揃えること
- メモ編集は `contenteditable` の行（`.memo-line-inp`）。**入力中は DOM を書き換えず読み取りだけ**にする（日本語 IME の変換を壊さないため）。DOM → 保存形式の変換は `domToRaw`、表示用は `renderInline`

## 実装上の約束事・落とし穴

- **タスクの並び**: `tasks` 配列の順 = 表示順。`syncFromNotion` で `order` 昇順に**一度だけ**整列し、以降はドラッグが配列を直接操作する。`renderAll` 側でソートしない（配列とずれてドラッグが壊れる）。新規タスクの `order` は「最大+1」で末尾に追加、ドラッグ後は `reindexActiveOrders()`
- **バージョン**: `APP_VERSION`（`index.html`）は連番 `vN`。変更を入れるたびに1つ上げる。日付形式にはしない
- **エスケープ**: メモは `escHtml`/`renderInline` で処理済み。タスク名・タグ名は `innerHTML` に**未エスケープのまま**埋め込まれている（編集時は既存挙動を踏襲しつつ慎重に）
- **動作確認**: プレビューが `data:` URL だと `localStorage` が使えずスクリプトが途中で止まる。確認用コピーに `localStorage` のスタブを足してから開く。確認用ファイルはコミットしない

## Capacitor アプリ（`capacitor-app/`）

- `capacitor.config.json` の `server.url` が **GitHub Pages の本番サイトを指す**ため、アプリは APK 内のファイルではなく**本番サイトを直接読み込む**。Web 側（`index.html`）だけの変更は **push すれば反映され、APK の再ビルドは不要**
- ネイティブ側（プラグイン・設定・アイコン）を変えたときだけ再ビルドが必要:
  `cd capacitor-app && npx cap sync android` → `android/` で `./gradlew assembleDebug` → `adb install -r`
- `capacitor-app/www/index.html` はルートの `index.html` のコピー。`server.url` 指定中は実行時には使われないが、整合のため `index.html` を変えたら `cp index.html capacitor-app/www/index.html` で揃える
- ビルド工程がないため `@capacitor/app` の JS ブリッジはバンドルされない。Capacitor 内でのみ CDN からプラグインを動的読み込みして `appUrlOpen` を購読している

## ウィジェット

- **`desktop-widget/`（Electron）**: `npm start`、またはデスクトップの `start.vbs`（作業ディレクトリは絶対パス指定）。トレイ常駐、Alt+N で表示切替。リサイズ可能で、サイズ・位置を `userData/window-state.json` に保存。`index.html` とは**コードを共有していない**ので、機能を足すときは手で揃える（タグフィルター・タグ管理・メモ編集に対応、メモの太字は未対応）
- **`android-widget/`（Kotlin）**: メモをタップすると `neonoting://memo?id=…` で Capacitor アプリを起動する

## デプロイ

`main` ブランチへの push で GitHub Pages が更新され、配信されるのはルートの `index.html`。
GAS 側のコード変更は別途 GAS エディタでデプロイが必要（このリポジトリ外）。

別 PC からの push があるので、push 前は `git pull --rebase`（未コミットの変更があるときは `--autostash`）。
コミットは自分の変更したファイルだけを指定して追加する。
