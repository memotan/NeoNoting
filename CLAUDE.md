# CLAUDE.md

## プロジェクト概要

**NeoNoting** — GitHub Pages 上で動作するモバイルファーストの PWA。タスクとメモを管理し、
Google Apps Script (GAS) で作ったプロキシ経由で Notion データベースに読み書きする。

- フロントエンドは **単一の HTML ファイル**（インライン CSS + バニラ JS、ビルド工程なし）
- バックエンドは GAS の Web アプリ（このリポジトリには**含まれない**。Notion トークンは GAS 側に保持）
- 依存は CDN の Tabler Icons (`@tabler/icons-webfont`) のみ。フレームワーク・パッケージマネージャなし

## ファイル構成

| ファイル | 役割 |
|---|---|
| `index.html` | GitHub Pages が配信するエントリポイント（git 履歴はこのファイルのみ）。**現状この作業ツリーの `index.html` は途中で切れた不完全な状態**（CSS 途中で終端）。修正前に必ず内容を確認すること |
| `index7.html` | 最新かつ最も完全なイテレーション。タスク + メモ両機能あり。**実装の参照元はこれ** |
| `index6.html` | index7 の一つ前。機能は同等、CSS の微調整のみ差分 |
| `index2.html`, `index3.html` | 初期版。タスク機能のみ（メモ機能なし） |
| `manifest.json` | PWA マニフェスト（standalone / portrait、SVG data-URI アイコン、theme #111） |

`index2 → 3 → 6 → 7` は手動バックアップ的な世代スナップショット。新しい変更は最新版をベースに行い、
公開する場合は `index.html` に反映する。

## アーキテクチャ

```
ブラウザ (index*.html)  ──POST JSON──▶  GAS Web アプリ  ──API──▶  Notion DB
       │                                  (Notion トークンを保持)
       └─ localStorage: nt_gas_url, nt_custom_tags
```

### GAS 通信プロトコル

すべて `gasCall(body)` 経由（`index7.html` 内）。`POST` で JSON を送り、
`{ ok: boolean, data, error }` を受け取る。`ok=false` なら `error` を投げる。

- **タスク**: `list` / `create` / `update` / `delete` / `reorder`
- **メモ**: `memo_list` / `memo_get` / `memo_create` / `memo_update` / `memo_delete`

データ形状:
- タスク: `{ id, name, done, tags: [{name, color}], order }`
- メモ: `{ id, name, lines: [{ type: 'text'|'numbered', text }] }`

### 状態と永続化

- `gasUrl` は **設定画面でユーザーが入力**し `localStorage` に保存（トークンはブラウザに置かない）
- 操作は**楽観的更新**: UI を即時更新 → GAS へ反映 → 失敗時は同期ドット（`.sdot`）を `err` に
- タグの色は `TAG_COLORS` 定数で定義。`customTags` はローカルと Notion 由来をマージ

## 開発上の注意

- ビルド・テスト・lint の仕組みはない。ブラウザ（モバイル幅 ≤480px）で直接動作確認する
- モバイルファースト: `#app` は `max-width:480px`、`env(safe-area-inset-*)` でノッチ対応、
  タッチドラッグ並び替えを独自実装（`setupDragTouch` / `touchmove` / `touchend`）
- UI 文言・コメントは日本語、CSS 変数によるダークテーマ（`--bg:#111` ほか）
- innerHTML へ値を差し込む箇所が多い。メモのプレビューは `escHtml` でエスケープ済みだが、
  タスク名・タグ名は未エスケープのまま埋め込まれている点に注意（編集時は既存挙動を踏襲しつつ慎重に）

## デプロイ

`main` ブランチへの push で GitHub Pages が更新される。配信されるのは `index.html`。
GAS 側のコード変更は別途 GAS エディタでデプロイが必要（このリポジトリ外）。
