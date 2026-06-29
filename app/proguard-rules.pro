# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# NanoHTTPD references optional javax.* / org slf4j classes that aren't present;
# keep it intact (release build currently has minify disabled anyway).
-dontwarn fi.iki.elonen.**
-keep class fi.iki.elonen.** { *; }
