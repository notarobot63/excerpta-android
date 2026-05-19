# Garder uniquement les Activity (référencées dans le manifest)
-keep public class xyz.notarobot.excerpta.*Activity { public <init>(); }
# BuildConfig accédé dans UpdateChecker
-keep class xyz.notarobot.excerpta.BuildConfig { *; }