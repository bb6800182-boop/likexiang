# 發佈版移除除錯 log，避免把行程內容寫進系統 log。
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# 下面兩條補套件附的規則不夠的地方：類別有留，但 R8 完整模式不會自動留無參數建構子，
# 用反射建立時就找不到（NoSuchMethodException）。

# WorkManager 的資料庫是 Room 2.6 產生的 WorkDatabase_Impl，Room 用反射建立；少了這條，正式版一啟動就當掉。
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# 月曆小工具的 ‹ › 用 actionRunCallback，Glance 用反射建立 ActionCallback；少了這條，按了就當掉。
-keepclassmembers class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
