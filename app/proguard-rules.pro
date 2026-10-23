# Modelos lidos e escritos pelo Gson via reflexão: os nomes dos campos são o JSON da API.
-keep class com.longynus.churrasco.data.model.** { *; }
-keepclassmembers class com.longynus.churrasco.data.model.** { <init>(...); }

# Retrofit com suspend e tipos genéricos (ApiResponse<T>).
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation interface com.longynus.churrasco.data.ChurrascoApi

# Firebase Realtime Database desserializa estas classes por reflexão.
-keep class com.longynus.churrasco.data.model.ChatMessage { *; }
-keep class com.longynus.churrasco.data.model.SharedLocation { *; }

# Stack traces legíveis no Crashlytics.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
