# crave-saver 忍住记

[![CI](https://github.com/MAXLINqaq/crave-saver/actions/workflows/android.yml/badge.svg)](https://github.com/MAXLINqaq/crave-saver/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg)](https://kotlinlang.org)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://developer.android.com)

嘴馋想点外卖时，把菜品加入购物车、走到支付确认页——**然后不付款**，打开本 App 记下这笔"忍住没花的钱"，看着净攒越滚越多。

## 玩法

1. 在美团/淘宝/京东等 App 里选好菜品，进入支付确认页，**截图，不付款**
2. 打开本 App 点"截图导入"，AI 自动识别店名/菜品/金额，**后台直接入账**，无需确认，切走 App 也不影响
3. 真吃了就用"吃一笔"记下实际消费——忍住总额减去吃了总额，才是真的省下来的钱

## 功能

- **忍住记 / 吃一笔**：主页左右滑动切换，两种记录各自统计，周期信息卡显示**净攒 = 忍住 − 吃了**
- **周期统计**：按月（每月几号起可自定，如 5 号）或固定 N 天滚动；主界面只显示当前周期，历史周期归档可查
- **AI 截图识别**：OpenAI 兼容多模态接口（默认硅基流动 Qwen3-VL-30B-A3B），打包费/配送费/优惠后价都能正确入账；WorkManager 后台执行 + 完成通知
- **连续忍住天数**：正反馈对抗冲动消费
- 纯本地存储（Room），无账号、无广告、无追踪；AI 配置只存在本机

## 下载安装

[Releases](../../releases) 页下载 APK 直接安装（手机需允许"安装未知来源应用"）。APK 用固定密钥签名，新版本可直接覆盖安装，数据不丢。

## AI 配置

首页右上角进入设置页，三栏都可改（任何 OpenAI 兼容服务均可）：

| 配置项 | 默认值 |
| --- | --- |
| Base URL | `https://api.siliconflow.cn/v1` |
| Model | `Qwen/Qwen3-VL-30B-A3B-Instruct` |
| API Key | 自行填写（[硅基流动](https://cloud.siliconflow.cn) 注册免费获取） |

一次识别约 3500 tokens，成本不足一分钱；不填 Key 时截图导入不可用，手动记账不受影响。

## 技术栈

单模块 MVVM：Kotlin 2.0 + Jetpack Compose（Material3）+ Room + Navigation + WorkManager + OkHttp，CI（GitHub Actions）跑单元测试 + 出固定签名的 release APK。最低 Android 8.0（API 26）。

```text
app/src/main/java/com/cravesaver/
├── data/       Room（金额以"分"存储，避免浮点误差）
├── ai/         AI 识别客户端 + WorkManager 后台任务
├── settings/   AI 配置 / 周期配置（SharedPreferences）
├── ui/         home（双页 Pager）/ add / history / cycle
└── util/       周期计算（纯函数，12 个 JVM 单测覆盖边界）
```

## 自己构建

用 Android Studio 打开本仓库即可（Gradle wrapper 已提交），或命令行：

```bash
./gradlew :app:assembleDebug
```

## 为什么不自动拦截支付

| 方案 | 可行性 | 说明 |
| --- | --- | --- |
| 自动拦截支付 | ❌ | 外卖/电商 App 无开放 API，无法在支付前拦截订单 |
| 手动快速记账 | ✅ | 兜底路径，始终可用 |
| 截图 + AI 识别 | ✅ | 本仓库采用：版式无关、准确率高、成本可忽略 |
| 无障碍服务监听 | ⚠️ | 脆弱（目标 App 改版即失效）、上架审核风险，不采用 |

## Roadmap

- [x] 手动记账 + 列表/统计
- [x] 截图导入 + AI 自动填单
- [x] 周期统计（按月/固定天数可自定义）+ 连续忍住天数
- [x] 吃一笔（实际消费记录）+ 净攒统计
- [x] 截图后台识别免确认自动入账（WorkManager + 通知）
- [x] 固定签名 Release 包（覆盖安装不丢数据）
- [x] 记录编辑、重复导入去重
- [ ] （远期）桌面小组件、周期趋势图、iOS 版本

## License

[MIT](LICENSE)
