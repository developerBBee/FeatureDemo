# 1. システム概要

## 1.1 目的

FeatureDemo は、Android の代表的な端末機能・ライブラリの使い方を 1 つのアプリ上で試せる
**機能デモアプリ** である。各機能は独立した画面として提供し、ホーム画面から選んで起動する。

- 対象利用者: Android アプリ開発者(機能の挙動確認・実装サンプルの参照)
- サーバー連携・ログイン・課金は持たない。データはすべて端末内で完結する
  (地図タイルの取得のみ Google Maps Platform と通信する)。

## 1.2 動作環境

| 項目 | 内容 |
| --- | --- |
| 対応 OS | Android 7.0(API 24)以上 |
| ターゲット / コンパイル SDK | API 36 |
| 画面向き | 制限なし(縦・横とも可) |
| 表示 | エッジ・ツー・エッジ表示。Material 3 テーマ。Android 12 以上ではダイナミックカラー、OS のダーク / ライト設定に追従 |
| 必須ハードウェア | なし(カメラは `required="false"`。カメラ非搭載端末にもインストール可能) |
| ネットワーク | 地図画面のみ必要(`INTERNET` 権限) |
| Google Play 開発者サービス | 位置情報(Fused Location Provider)、地図、ML Kit で利用 |

機能ごとの OS バージョン依存は以下のとおり。

| 機能 | 条件 | 条件を満たさない場合 |
| --- | --- | --- |
| 通知権限の実行時要求 | Android 13(API 33)以上 | 12 以下は権限不要として扱う |
| 画像変換: HEIC 出力 | Android 9(API 28)以上 | 選択肢がグレーアウトされ選べない |
| 生体情報の登録画面への誘導 | Android 11 以上: 生体認証登録画面 / 9〜10: 指紋登録画面 / 8 以下: セキュリティ設定画面 | — |

## 1.3 機能一覧

| ID | 機能名 | ホームのボタン名 | 概要 | 主な利用技術 |
| --- | --- | --- | --- | --- |
| F-01 | リスト・詳細表示 | (リスト項目) | ダミー項目一覧から詳細画面へ引数付きで遷移する | Navigation 3 |
| F-02 | 生体認証 | 「生体認証デモ」 | 端末の生体認証(Class 3 / Strong)で認証し、成功画面へ遷移する | androidx.biometric |
| F-03 | 外部アプリ起動 | 「外部アプリ起動デモ」 | 入力したタイトル・本文をメール / SMS / 共有アプリへ渡す、またはテキストファイルとして保存する | Intent、Storage Access Framework |
| F-04 | バーコード読み取り | 「バーコード読み取りデモ」 | カメラ映像からバーコード / QR コードを検出し、内容を重ねて表示する。URL はタップで開ける | CameraX、ML Kit Barcode Scanning |
| F-05 | 文字スキャン | 「文字スキャンデモ」 | カメラ映像からラテン文字を認識し、テキストをコピーできる | CameraX、ML Kit Text Recognition |
| F-06 | 顔検出 | 「顔検出デモ」 | カメラ映像から顔を検出し、笑顔・目の開き具合の確率を表示する | CameraX、ML Kit Face Detection |
| F-07 | 通知 | 「通知デモ」 | 3 種類の通知(メッセージ / 画面を開く / 選択肢付き)を表示する | NotificationCompat、BroadcastReceiver |
| F-08 | 日毎の移動経路 | 「日毎の移動経路デモ」 | 位置情報を記録し、日付ごとの移動経路を地図上に描画する | Fused Location Provider、フォアグラウンドサービス、Room、Maps Compose |
| F-09 | 画像形式変換 | 「画像形式変換デモ」 | 選択した画像を PNG / JPEG / WebP / GIF / BMP / HEIC に変換して保存する | Photo Picker、SAF、HeifWriter |
| F-10 | デバッグ画面(debug のみ) | (常駐通知から起動) | DataStore と Room データベースの内容を一覧表示する | DataStore、Room |
| F-11 | 起動記録 | (自動) | アプリ起動回数と最終起動時刻を記録する | Preferences DataStore |

## 1.4 ビルドバリアント

| 項目 | debug | release |
| --- | --- | --- |
| F-01〜F-09、F-11 | あり | あり |
| デバッグ画面(F-10) | あり | **なし**(コード・画面とも APK に含まれない) |
| デバッグ用常駐通知 | アプリ起動時に自動表示 | なし |
| 追加権限 | `FOREGROUND_SERVICE_SPECIAL_USE` | なし |
| 難読化(minify) | なし | なし |
| 署名 | `local.properties` にキーストア設定があればその鍵、なければ Android SDK 標準の debug 鍵 | 未設定(別途署名が必要) |

## 1.5 ビルド時設定

ビルド時に外部から与える設定値。いずれも未設定でもビルドは成功する。

| 設定名 | 用途 | 指定方法(優先順) | 未設定時の振る舞い |
| --- | --- | --- | --- |
| `MAPS_API_KEY` | Google Maps SDK の API キー | `local.properties` → Gradle プロパティ(`gradle.properties` / `-PMAPS_API_KEY`)→ 環境変数 | ビルド時に警告を出力。地図が描画されない(経路の記録は可能) |
| `KEYSTORE_PATH` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` | debug ビルドの署名鍵 | `local.properties`(4 項目すべて必要) | 標準の debug 鍵で署名。パスのファイルが存在しない場合は警告を出して標準鍵を使用 |

## 1.6 制約事項

- 文字スキャンはラテン文字(英数字など)のみ対象。日本語は認識しない。
- 位置情報はアプリがバックグラウンド位置情報権限(`ACCESS_BACKGROUND_LOCATION`)を要求しない。
  記録は「記録中」のフォアグラウンドサービス通知が表示されている間のみ行われる。
- 記録した位置情報を削除・エクスポートする機能はない(アプリのデータ消去またはアンインストールで削除される)。
- 通知デモのステータス(承認 / 却下)はメモリ上のみで保持し、プロセス終了で「未対応」に戻る。
- 画面文言は日本語固定(多言語化していない)。

## 参考(公式ドキュメント)

- [Navigation 3](https://developer.android.com/guide/navigation/navigation-3)
- [Build variants の設定](https://developer.android.com/build/build-variants)
- [Material Design 3 in Compose(ダイナミックカラー)](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [アプリをエッジ・ツー・エッジで表示する](https://developer.android.com/develop/ui/compose/system/setup-e2e)
