# Strip all logging from release builds
-assumenosideeffects class android.util.Log { *; }
-repackageclasses ''
-allowaccessmodification
