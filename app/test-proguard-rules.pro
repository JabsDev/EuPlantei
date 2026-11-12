# O APK instrumentado não precisa ser minificado; evita remover classes de runtime
# Kotlin/Android usadas apenas pelo runner de testes.
-dontshrink
-dontoptimize
-dontobfuscate
-dontwarn javax.lang.model.**
-dontwarn com.google.errorprone.annotations.**
