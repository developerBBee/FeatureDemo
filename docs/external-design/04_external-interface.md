# 4. 外部インターフェース設計

アプリが OS・他アプリ・外部サービスとやり取りする境界の仕様を定義する。

## 4.1 権限

### 4.1.1 宣言する権限

| 権限 | 種別 | 用途 | 要求する画面・契機 | ビルド |
| --- | --- | --- | --- | --- |
| `INTERNET` | 通常(インストール時) | 地図タイルの取得 | — | 共通 |
| `CAMERA` | 実行時 | バーコード読み取り・文字スキャン・顔検出 | SC-06 / SC-07 / SC-08 を開いた時 | 共通 |
| `POST_NOTIFICATIONS` | 実行時(Android 13 以上) | 通知の表示 | SC-09 のボタン押下時、SC-10 の記録開始時、debug ビルドの起動時 | 共通 |
| `ACCESS_FINE_LOCATION` | 実行時 | 経路記録(高精度) | SC-10「権限を許可する」 | 共通 |
| `ACCESS_COARSE_LOCATION` | 実行時 | 経路記録(おおよその位置のみ許可された場合) | SC-10「権限を許可する」(FINE と同時に要求) | 共通 |
| `FOREGROUND_SERVICE` | 通常 | フォアグラウンドサービスの実行 | — | 共通 |
| `FOREGROUND_SERVICE_LOCATION` | 通常 | 位置情報タイプのフォアグラウンドサービス | — | 共通 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | 通常 | 特殊用途タイプのフォアグラウンドサービス(デバッグ常駐通知) | — | debug のみ |

要求しない権限:

- `ACCESS_BACKGROUND_LOCATION`: 記録はフォアグラウンドサービスで行うため不要。
- ストレージ権限(`READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` など): 画像の読み込みは写真選択ツール、
  ファイル保存は Storage Access Framework を使い、利用者が選んだファイルのみにアクセスするため不要。
- `USE_BIOMETRIC`: androidx.biometric ライブラリのマニフェストから自動的にマージされる。

### 4.1.2 権限が拒否された場合

| 権限 | 振る舞い |
| --- | --- |
| `CAMERA` | 案内文と「カメラ権限を許可する」ボタンを表示。OS が再表示を抑止した(「今後表示しない」相当)場合は、ボタンを押してもダイアログが出ないため OS の設定画面から許可する必要がある |
| 位置情報 | 案内画面を表示し地図を表示しない。画面に戻るたびに権限状態を再確認する |
| `POST_NOTIFICATIONS` | [通知設計 3.4](03_notification-design.md#34-通知権限android-13-以上) 参照 |

## 4.2 他アプリ連携(Intent)

### 4.2.1 アプリから他アプリを呼び出す

| 呼び出し元 | 連携先 | アクション / データ | 渡す値 | 連携先がない場合 |
| --- | --- | --- | --- | --- |
| SC-05「メールアプリを起動」 | メールアプリ | `ACTION_SENDTO` / `mailto:` | `EXTRA_SUBJECT` = タイトル、`EXTRA_TEXT` = 本文 | 「対応するアプリが見つかりませんでした」 |
| SC-05「メッセージアプリを起動」 | SMS アプリ | `ACTION_SENDTO` / `smsto:` | `sms_body` = タイトルと本文を改行で連結(空の項目は除外) | 同上 |
| SC-05「共有アプリを選択」 | テキストを受け取れる任意のアプリ(OS の共有シート) | `ACTION_SEND` / `text/plain`(チューザー経由) | `EXTRA_TEXT` = タイトルと本文を改行で連結(空の項目は除外)、`EXTRA_SUBJECT` = タイトル(空白のみの場合は渡さない) | 同上 |
| SC-05「ファイルとして保存」 | OS のファイル保存画面 | `ACTION_CREATE_DOCUMENT` / `text/plain` | 既定ファイル名 | — |
| SC-06 URL ラベルのタップ | ブラウザーなど | `ACTION_VIEW` / 読み取った URL | — | 何もしない |
| SC-03「生体情報を登録する」 | OS 設定 | Android 11 以上: `ACTION_BIOMETRIC_ENROLL`(Strong を指定)/ 9〜10: `ACTION_FINGERPRINT_ENROLL` / 8 以下: `ACTION_SECURITY_SETTINGS` | — | — |
| SC-11「画像を選択」 | OS の写真選択ツール | `PickVisualMedia`(画像のみ) | — | — |
| SC-11「変換して保存」 | OS のファイル保存画面 | `ACTION_CREATE_DOCUMENT` / 選択形式の MIME タイプ | 既定ファイル名 | — |

### 4.2.2 他アプリ・OS からアプリが呼び出される

| コンポーネント | 外部公開 | 呼び出し元 | 仕様 |
| --- | --- | --- | --- |
| `MainActivity` | 公開(ランチャー) | ホーム画面のアイコン、アプリ内通知 | ランチャーから起動すると SC-01 を表示。Intent エクストラ `extra_destination` が `notification_demo` なら SC-09、`debug_menu` なら SC-12(debug のみ)を開く。それ以外の値は無視する |
| `NotificationActionReceiver` | 非公開 | アプリ自身の通知(NT-03 のアクション) | アクション `jp.developer.bbee.featuredemo.action.UPDATE_STATUS`、エクストラ `extra_status` = `APPROVED` / `REJECTED` を受け取りステータスを更新する。不明な値は「未対応」として扱う |
| `LocationTrackingService` | 非公開 | アプリ自身 | 経路記録のフォアグラウンドサービス |
| `DebugNotificationService` | 非公開 | アプリ自身 | debug のみ。デバッグ常駐通知のフォアグラウンドサービス |

非公開コンポーネントには、アプリ内で発行した PendingIntent(すべて変更不可 `FLAG_IMMUTABLE`)経由でのみ到達する。

## 4.3 OS 機能・端末機能の利用

### 4.3.1 フォアグラウンドサービス

| サービス | タイプ | 起動契機 | 終了契機 | OS による強制終了後 |
| --- | --- | --- | --- | --- |
| 経路記録 | `location` | SC-10「● 記録開始」 | SC-10「■ 記録停止」 | OS が再起動を試みる(再起動されると記録を再開) |
| デバッグ常駐通知(debug のみ) | `specialUse`(サブタイプ `debug_tools_notification`) | アプリ起動時 / SC-12「常駐通知を再表示」 | NT-05「停止」 | OS が再起動を試みる |

- フォアグラウンドサービスの開始が OS に拒否された場合(バックグラウンドからの起動制限など)、
  アプリはクラッシュせずサービスを終了する。経路記録の場合は SC-10 の表示が「● 記録開始」に戻る。
- 位置情報権限がない状態でサービスが起動・再起動された場合(記録中に権限を取り消した場合など)は、記録を停止する。

### 4.3.2 位置情報の取得条件

| 項目 | 値 |
| --- | --- |
| 取得元 | Google Play 開発者サービスの Fused Location Provider |
| 精度 | 高精度優先(おおよその位置のみ許可されている場合は OS がおおよその位置を返す) |
| 取得間隔 | 30 秒 |
| 最小移動距離 | 10 m(前回から 10 m 未満の移動では記録しない) |
| 保存 | 受信した地点をすべて記録する(OS が複数地点をまとめて配信した場合も欠落させない) |

### 4.3.3 カメラ・画像解析

| 画面 | 解析内容 | 処理場所 |
| --- | --- | --- |
| SC-06 | バーコード(全フォーマット、複数同時) | 端末内(ML Kit) |
| SC-07 | テキスト認識(ラテン文字) | 端末内(ML Kit) |
| SC-08 | 顔検出(速度優先、笑顔・目の開閉の分類、トラッキングあり) | 端末内(ML Kit) |

カメラ映像・解析結果は端末外へ送信せず、保存もしない。

### 4.3.4 生体認証

| 項目 | 値 |
| --- | --- |
| 許可する認証方式 | `BIOMETRIC_STRONG`(Class 3)のみ |
| 端末認証情報(PIN 等)による代替 | 許可しない |
| 暗号鍵との連携(CryptoObject) | なし(認証成否の確認のみ) |

### 4.3.5 クリップボード

SC-07「読み取った文字をコピー」で、認識テキストをプレーンテキスト(ラベル `recognized_text`)としてコピーする。

## 4.4 外部サービス

| サービス | 用途 | 通信内容 | 認証 |
| --- | --- | --- | --- |
| Google Maps Platform(Maps SDK for Android) | SC-10 の地図表示 | 地図タイル等の取得(SDK が通信) | ビルド時に埋め込む API キー(`MAPS_API_KEY`)。[システム概要 1.5](01_system-overview.md#15-ビルド時設定) 参照 |
| Google Play 開発者サービス | 位置情報、ML Kit モデル | SDK 経由 | なし |

記録した位置情報やその他のアプリ内データを、アプリが独自に外部サーバーへ送信することはない。

## 参考(公式ドキュメント)

- [Android での権限](https://developer.android.com/guide/topics/permissions/overview)
- [実行時の権限をリクエストする](https://developer.android.com/training/permissions/requesting)
- [フォアグラウンド サービスのタイプ](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [位置情報の更新をリクエストする](https://developer.android.com/develop/sensors-and-location/location/request-updates)
- [一般的なインテント](https://developer.android.com/guide/components/intents-common)
- [Android Sharesheet を使用してデータを送信する](https://developer.android.com/training/sharing/send)
- [ドキュメントやその他のファイルにアクセスする(SAF)](https://developer.android.com/training/data-storage/shared/documents-files)
- [PendingIntent の可変性](https://developer.android.com/guide/components/intents-filters#DeclareMutabilityPendingIntent)
- [Maps SDK for Android: API キーの使用](https://developers.google.com/maps/documentation/android-sdk/get-api-key)
