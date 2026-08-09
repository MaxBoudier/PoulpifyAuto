# DTO kotlinx.serialization : les serializers sont generes, mais R8 doit
# conserver les classes et leur Companion pour les retrouver.
-keepclassmembers class fr.maxboudier.poulpifyauto.**$$serializer { *; }
-keepclasseswithmembers class fr.maxboudier.poulpifyauto.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class fr.maxboudier.poulpifyauto.core.network.dto.** { *; }

# Retrofit : les interfaces et leurs annotations doivent survivre.
-keep,allowobfuscation interface fr.maxboudier.poulpifyauto.core.network.PoulpifyApi
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations

# Les regles du SDK Spotify App Remote arrivent via consumer-rules.pro de
# :core:spotify ; celles-ci couvrent le cas ou l'AAR est consomme directement.
-keep class com.spotify.protocol.types.** { *; }
-dontwarn com.spotify.**
