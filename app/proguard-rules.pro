# Garder uniquement les Activity (référencées dans le manifest)
-keep public class xyz.notarobot.linky.*Activity { public <init>(); }
# BuildConfig accédé dans UpdateChecker
-keep class xyz.notarobot.linky.BuildConfig { *; }
