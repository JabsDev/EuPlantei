# Mantém as APIs JNI e as classes de serialização usadas pelo app.
-keep class br.com.jabsdev.euplantei.ml.NativeLlamaBridge { *; }
-keep class br.com.jabsdev.euplantei.ml.LlamaTokenCallback { *; }
-keepattributes *Annotation*
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class br.com.jabsdev.euplantei.**$$serializer { *; }
-keepclassmembers class br.com.jabsdev.euplantei.** { *** Companion; }
-keepclasseswithmembers class br.com.jabsdev.euplantei.** { kotlinx.serialization.KSerializer serializer(...); }

# Anotações de compile-time usadas por dependências de teste; não existem no Android.
-dontwarn javax.lang.model.**
-dontwarn com.google.errorprone.annotations.**

# O APK instrumentado referencia utilitários Kotlin do APK principal.
-keep class kotlin.** { *; }

# A API de teste em memória do Room só é usada no APK instrumentado.
-keep class androidx.room.Room { *; }
-keep class androidx.room.RoomDatabase { *; }
-keep class androidx.room.RoomDatabase$Builder { *; }
-keep class androidx.room.RoomOpenHelper { *; }
-keep class androidx.room.RoomOpenHelper$Delegate { *; }
-keep class * extends androidx.room.RoomOpenHelper$Delegate { *; }
-keep class androidx.room.RoomOpenDelegate { *; }

# O APK instrumentado chama os DAOs diretamente; sem isso o R8 pode remover
# métodos públicos não usados pelo fluxo da UI.
-keep interface br.com.jabsdev.euplantei.data.db.** { *; }
-keep class * implements br.com.jabsdev.euplantei.data.db.** { *; }

# O APK instrumentado usa corrotinas do APK principal.
-keep class kotlinx.coroutines.** { *; }

# Default parameters das interfaces de ML usados pelos fakes instrumentados.
-keep interface br.com.jabsdev.euplantei.ml.** { *; }
-keep class br.com.jabsdev.euplantei.ml.**$DefaultImpls { *; }
