# ── Keep all parent app classes ────────────────────────────────────────────
-keep class com.parent.monitor.** { *; }
-keepclassmembers class com.parent.monitor.** { *; }

# ── OkHttp WebSocket ───────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ── JSON ───────────────────────────────────────────────────────────────────
-keep class org.json.** { *; }

# ── Gson ───────────────────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# ── WorkManager ────────────────────────────────────────────────────────────
-keep class androidx.work.** { *; }
-keepclassmembers class * extends androidx.work.Worker { public <init>(android.content.Context, androidx.work.WorkerParameters); }

# ── Google Maps ────────────────────────────────────────────────────────────
-keep class com.google.android.gms.maps.** { *; }
-dontwarn com.google.android.gms.**

# ── ZXing QR ──────────────────────────────────────────────────────────────
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.** { *; }

# ── Material Components (BottomNavigationView, etc.) ──────────────────────
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**
-keepclassmembers class com.google.android.material.** { *; }

# ── AndroidX ──────────────────────────────────────────────────────────────
-keep class androidx.** { *; }
-dontwarn androidx.**
-keepclassmembers class androidx.** { *; }

# ── Firebase Auth ─────────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ── View Binding / Fragments ───────────────────────────────────────────────
-keepclassmembers class * extends androidx.fragment.app.Fragment { *; }

# ── Kotlin ────────────────────────────────────────────────────────────────
-keepattributes Kotlin*
-keep class kotlin.** { *; }
-dontwarn kotlin.**

# ── R classes ─────────────────────────────────────────────────────────────
-keepclassmembers class **.R$* { public static <fields>; }
