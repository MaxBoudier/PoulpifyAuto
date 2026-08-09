# Le SDK App Remote deserialise ses messages par reflexion Gson : sans ces
# regles, R8 renomme les champs et la connexion echoue silencieusement en
# release alors qu'elle marche en debug.
-keep class com.spotify.protocol.types.Item
-keep class * implements com.spotify.protocol.types.Item { *; }
-keep class com.spotify.android.appremote.internal.DebugSpotifyLocator
-keep class com.spotify.android.appremote.internal.ReleaseSpotifyLocator
-keep class com.spotify.android.appremote.api.ConnectionParams$Builder

-dontwarn com.spotify.android.appremote.api.ContentApi$ContentType
-dontwarn com.spotify.android.appremote.api.PlayerApi$StreamType
-dontwarn com.spotify.protocol.types.*
-dontwarn com.fasterxml.jackson.*
