# FIRE

纯本地的 FIRE 规划应用。净资产、月收入、月支出、安全提取率和预期年化收益率都算在这台手机上，没有账号，也没有自己的后端。聊天和每日提醒始终请求你在设置里填写的 OpenAI 兼容接口；请求失败就显示错误，不会换成写死的鼓励文案。

不上传应用商店，也没有 AAB。能安装的是 GitHub Releases 上的通用 APK。

## 安装

1. 打开本仓库的 [Releases](../../releases)，下载 `fireplan-x.y.z.apk`。
2. 把 APK 拷到安卓手机（Android 8.0 / API 26 及以上）。
3. 安装时如果系统拦截，到设置里允许「安装未知应用」（有的手机写成「安装未知来源应用」）。
4. 打开应用，在「设置」里填写接口根地址、API Key 和模型名。地址可以是 `https://api.openai.com/v1`，也可以是局域网里的 `http://` 地址。

想在正式版之前先装一台，可以到 GitHub Actions 的 CI 运行记录里下载调试 APK。调试包用 Android 默认调试证书，不需要下面的签名 secret。

## 发布签名

第一次推送 `v*` 标签（例如 `v0.1.0`）之前，在仓库的 Settings → Secrets and variables → Actions 里配好这四个 secret。缺任何一个，发布工作流会直接失败，不会上传未签名的包。

| Secret | 含义 |
| --- | --- |
| `KEYSTORE_BASE64` | 发布密钥库文件的 base64 |
| `KEYSTORE_PASSWORD` | 密钥库口令 |
| `KEY_ALIAS` | 密钥别名 |
| `KEY_PASSWORD` | 密钥口令 |

密钥库不要提交到仓库。

```bash
keytool -genkeypair -v -keystore release.keystore -alias fireplan -keyalg RSA -keysize 2048 -validity 10000
base64 -w 0 release.keystore
```

把 base64 整段存进 `KEYSTORE_BASE64`，另外三个 secret 填密钥库口令、别名和密钥口令。然后：

```bash
git tag v0.1.0
git push origin v0.1.0
```

版本名来自标签（`v0.1.0` → `0.1.0`），`versionCode` 使用该次 Actions 的 `GITHUB_RUN_NUMBER`，后一次发布可以覆盖安装前一次。下载入口就是仓库的 Releases 页面，文件名形如 `fireplan-0.1.0.apk`。
