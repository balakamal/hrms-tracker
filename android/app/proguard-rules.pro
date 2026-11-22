# HRMS Insights ProGuard / R8 Optimization Rules

# 1. Preserve JavaScript Interface methods
-keepclassmembers class com.balakamal.hrmstracker.MainActivity$WebAppInterface {
    @android.webkit.JavascriptInterface <methods>;
}

# 2. Preserve WorkManager Worker reflection constructors
-keep public class com.balakamal.hrmstracker.AttendanceWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep public class com.balakamal.hrmstracker.TimesheetReminderWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# 3. Preserve App Widgets and Broadcast Receivers
-keep class com.balakamal.hrmstracker.AttendanceAppWidgetProvider** { *; }
-keep class com.balakamal.hrmstracker.Widget*Provider** { *; }
-keep class com.balakamal.hrmstracker.BootReceiver { *; }
-keep class com.balakamal.hrmstracker.ShiftAlertReceiver { *; }

# 4. Preserve line numbers and source file attributes for debugging / crash reports
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
