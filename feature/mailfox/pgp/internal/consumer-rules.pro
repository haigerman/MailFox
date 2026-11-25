# MailFox: Bouncy Castle registers its algorithms via class names (reflection).
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# PGPainless
-keep class org.pgpainless.** { *; }
-dontwarn org.pgpainless.**
-dontwarn org.slf4j.**
