# Churrasco 🍖

App Android para organizar churrascos com os amigos: crie o evento, convide a galera, combine quem leva o quê, converse no chat e compartilhe a localização para todo mundo chegar.

Backend: [fcm-server](https://github.com/arthursucka/fcm-server).

## Download

Baixe o APK mais recente na aba **[Releases](https://github.com/arthursucka/Churrasco/releases/latest)**.

## Stack

- Kotlin + Jetpack Compose
- Firebase (Auth, Realtime Database, Cloud Messaging, Crashlytics)
- minSdk 21 · targetSdk 35

## Como compilar

1. Abra o projeto no Android Studio (JDK 17).
2. Garanta que `app/google-services.json` aponta para o seu projeto Firebase.
3. Rode:

```bash
./gradlew assembleDebug
```

### Build de release assinada

Crie um `keystore.properties` na raiz (ele já é ignorado pelo git):

```properties
storeFile=caminho/para/sua.jks
storePassword=...
keyAlias=...
keyPassword=...
```

E rode `./gradlew assembleRelease`. O APK gerado deve ser publicado como uma nova Release no GitHub — não faça commit de APKs no repositório.

## Regras do Firebase

As regras do Realtime Database ficam em `firebase-database-rules.json` e são publicadas com:

```bash
firebase deploy --only database
```
