---
version: alpha
name: calendar-alarm-warm
description: "曆刻響 App 的設計系統，以 Notion 的暖白紙感為基底（2026-10-11 選定介面範例 C「溫暖色塊」）。暖白底、炭灰字、單一紫色重點；行程沿用 Google 月曆的顏色，照 Google 小工具的畫法鋪成色塊；響鈴畫面反轉成海軍藍底，是整套唯一的深色畫面。"

colors:
  primary: "#5645d4"
  on-primary: "#ffffff"
  canvas: "#ffffff"
  surface: "#f6f5f4"
  hairline: "#e5e3df"
  hairline-strong: "#c8c4be"
  ink: "#1a1a1a"
  charcoal: "#37352f"
  slate: "#5d5b54"
  steel: "#787671"
  alarm-tint: "#e6e0f5"
  today-tint: "#eeecfb"
  error: "#b3261e"
  ring-bg: "#0a1530"
  ring-surface: "#1a2a52"
  ring-ink: "#ffffff"
  ring-ink-muted: "#a4a097"
  ring-glow: "#d6b6f6"
  deco-orange: "#dd5b00"
  deco-pink: "#ff64c8"
  deco-yellow: "#f5d75e"
  deco-teal: "#2a9d99"
  deco-green: "#1aae39"

typography:
  title-lg:
    fontFamily: "Inter, Noto Sans TC, sans-serif"
    fontSize: 20px
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: -0.5px
  title-md:
    fontFamily: "Inter, Noto Sans TC, sans-serif"
    fontSize: 17px
    fontWeight: 600
    lineHeight: 1.3
    letterSpacing: -0.2px
  body:
    fontFamily: "Inter, Noto Sans TC, sans-serif"
    fontSize: 15px
    fontWeight: 400
    lineHeight: 1.5
  body-sm:
    fontFamily: "Inter, Noto Sans TC, sans-serif"
    fontSize: 13px
    fontWeight: 400
    lineHeight: 1.45
  caption:
    fontFamily: "Inter, Noto Sans TC, sans-serif"
    fontSize: 12px
    fontWeight: 500
    lineHeight: 1.4
  chip:
    fontFamily: "Inter, Noto Sans TC, sans-serif"
    fontSize: 11px
    fontWeight: 500
    lineHeight: 1.45
  lunar:
    fontFamily: "Inter, Noto Sans TC, sans-serif"
    fontSize: 9.5px
    fontWeight: 400
    lineHeight: 1.2
  ring-title:
    fontFamily: "Inter, Noto Sans TC, sans-serif"
    fontSize: 26px
    fontWeight: 600
    lineHeight: 1.25
    letterSpacing: -0.5px
  clock:
    fontFamily: "Inter, Noto Sans TC, sans-serif"
    fontSize: 64px
    fontWeight: 600
    lineHeight: 1.05
    letterSpacing: -2.5px

rounded:
  xs: 4px
  sm: 6px
  md: 8px
  lg: 12px
  xl: 16px
  xxl: 20px
  full: 9999px

spacing:
  xxs: 4px
  xs: 8px
  sm: 12px
  md: 16px
  lg: 20px
  xl: 24px
  xxl: 32px

components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.body}"
    rounded: "{rounded.md}"
    padding: 10px 18px
  button-outline:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.md}"
    padding: 6px 12px
  fab:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    rounded: "{rounded.xl}"
    size: 56px
  today-marker:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.caption}"
    rounded: "{rounded.full}"
  today-cell:
    backgroundColor: "{colors.today-tint}"
    textColor: "{colors.ink}"
    typography: "{typography.caption}"
  fab-voice:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.primary}"
    rounded: "{rounded.full}"
  event-chip:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.chip}"
    rounded: "{rounded.xs}"
    padding: 0 4px
  option-chip:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.charcoal}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.md}"
    padding: 6px 12px
  option-chip-selected:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.md}"
    padding: 6px 12px
  text-field:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.md}"
  form-error:
    textColor: "{colors.error}"
    typography: "{typography.caption}"
  alarm-card:
    backgroundColor: "{colors.alarm-tint}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.lg}"
    padding: 12px 14px
  ring-screen:
    backgroundColor: "{colors.ring-bg}"
    textColor: "{colors.ring-ink}"
    typography: "{typography.clock}"
  ring-title:
    textColor: "{colors.ring-ink}"
    typography: "{typography.ring-title}"
  ring-snooze:
    backgroundColor: "{colors.ring-surface}"
    textColor: "{colors.ring-ink}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.md}"
    height: 44px
  ring-dismiss:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.title-md}"
    rounded: "{rounded.md}"
    height: 52px
---

## Overview

暖白底（`{colors.canvas}`）配炭灰字（`{colors.ink}`、`{colors.charcoal}`），只用一個紫色 `{colors.primary}` 當重點：今天、主要按鈕、選取中的選項、開關。資訊靠留白與細線（`{colors.hairline}`）分層，不靠陰影。

這是一支「取代手機上 Google 月曆」的 App，所以首頁是整頁月曆：每一格直接看到當天行程，點日期才進入當天的時間軸。

## Colors

- **行程色塊**：照 Google 月曆小工具的畫法：Google 現在的色票（手機資料庫裡的 11 種行程顏色與 24 種日曆顏色都還是舊色票，依名稱換成新色票；國定假日日曆的 #16A765 照 Google 實際顯示當尤加利樹綠）在 OKLab 加兩成白，鮮明但不刺眼。月曆格、時間軸、小工具的色塊，以及清單的色條、色點都用這個顏色。字：底色相對亮度低於 0.40 用白字（`{colors.on-primary}`），否則用同色系深色字（底色往黑 85%，對比 8:1 以上）。**這是全 App 唯一的對比例外**：中間色上的白字只有約 2.6～4:1，低於 WCAG AA；使用者看過對照圖後，選擇跟 Google 月曆一樣（2026-10-11）。`components.event-chip` 的顏色只是示意。
- **鬧鐘**：App 裡一律用鬧鐘線條圖示（顏色跟著文字走）；⏰ 字元只出現在 Google 月曆的標題裡。鬧鐘設定卡用 `{colors.alarm-tint}`。
- **響鈴畫面**：`{colors.ring-bg}` 海軍藍底、`{colors.ring-ink}` 白字，裝飾小圓點用五個 deco 色，關閉鍵用 `{colors.primary}`。
- **次要文字**：白底與 `{colors.surface}` 上用 `{colors.slate}`；行程色塊上的時間、地點跟標題同一個字色。`{colors.steel}` 只用在白底（非本月日期、輸入框外框與提示字）。
- **目前時間線**：日檢視的「現在」橫線用 `{colors.primary}`，跟今天標記同一個語意。
- **今天**：日期用 `components.today-marker`（紫底白字圓形）。桌面的月曆小工具整頁一眼看過去不好找，今天那一格再整格鋪 `{colors.today-tint}`（重點色約 10% 疊在白底），格上的字照常用 `{colors.ink}`、`{colors.slate}`。
- **錯誤訊息**：表單錯誤用 `{colors.error}`（白底對比 6.5:1），只用在錯誤，不當裝飾。
- **App 圖示**：北歐扁平風，月曆頁加一個響著的鈴；用自己的色票，不算介面重點色：底 `#4F6D7A`（峽灣藍）、月曆頁 `#F4EFE6`、頁首與聲波 `#D9785B`（陶土橘），鈴與底同色。只用在啟動器圖示、說明網站與作品入口網站。
- **未開啟的開關、未勾選的方框**：外框與圓鈕用 `{colors.slate}`，才達到 3:1 的元件對比；`{colors.hairline-strong}` 只當分隔與按鈕外框。

## Typography

系統字體（Android 上是 Roboto 與 Noto Sans CJK）。標題 600、內文 400，標題字距略收。月曆格的行程名稱用 `{typography.chip}`，農曆用 `{typography.lunar}`，放不下就裁掉，不加刪節號。

## Layout

4dp 為基本單位。月曆格每週一列、平均分配高度；每格最上面是日期＋農曆，下面是行程色塊（每條 16dp 高、間距 2dp），放不下時最後一條改成「+N」。跨日行程橫跨多格。

## Elevation & Depth

幾乎不用陰影：分層靠 `{colors.surface}` 與細線。只有浮動新增鍵（`components.fab`）有一層柔和陰影（約 6dp）。

## Shapes

行程色塊 `{rounded.xs}`，按鈕與輸入框 `{rounded.md}`，卡片 `{rounded.lg}`，浮動新增鍵 `{rounded.xl}`，今天標記 `{rounded.full}`。

## Components

- `button-primary`：儲存、關閉鬧鐘等主要動作。
- `button-outline`：頂部「今天」。
- `fab` / `fab-voice`：右下角新增鍵；上方 48dp 的圓是語音新增（白底、`{colors.hairline-strong}` 細框、麥克風用 `{colors.primary}`，次要動作不加陰影）。小工具頂部的麥克風跟上下月箭頭一樣是線條圖示，只有「＋」用紫底；1×1 語音小工具整顆是紫底白色麥克風。
- `today-marker`、`today-cell`：今天的日期圓形；月曆小工具裡今天那一格。
- `option-chip` / `option-chip-selected`：提前時間、一般通知等單選。
- `alarm-card`：新增／編輯與詳情頁裡的鬧鐘設定區。
- `text-field`：一般輸入框（對話框裡的數字等），未選取時外框 `{colors.steel}`、選取時 `{colors.primary}`。新增／編輯頁照 Google 月曆的編輯頁，標題、地點、說明改用無外框的輸入列：左邊圖示、列與列之間細線，聚焦時底下一條 `{colors.primary}` 的線，提示字用 `{colors.steel}`。
- `form-error`：輸入框下方的錯誤訊息。
- `ring-title`、`ring-snooze`、`ring-dismiss`：響鈴畫面的行程標題、稍後提醒（「5 分後再響」）與關閉。

## Do's and Don'ts

- Do：顏色、字級、間距、圓角一律引用 token（程式對應在 `ui/theme/Tokens.kt`）。
- Do：同一語意只用同一個 token，主要動作永遠是 `{colors.primary}`。
- Don't：不要在白底以外的地方用 `{colors.steel}` 當文字色。
- Don't：不要加第二個重點色；行程顏色來自使用者的 Google 月曆，不算介面重點色。
- Don't：行程色塊以外，文字對比不要低於 4.5:1（行程色塊的白字是唯一例外，見「行程色塊」）。
