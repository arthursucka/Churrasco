# Churrasco 🍖

App Android para organizar churrascos com os amigos: crie o evento, convide a galera, combine quem leva o quê, converse no chat e compartilhe a localização para todo mundo chegar.

Backend: [fcm-server](https://github.com/arthursucka/fcm-server).

## Download

Baixe o APK mais recente na aba **[Releases](https://github.com/arthursucka/Churrasco/releases/latest)**.

## Stack

- Kotlin + Android Views (XML) + Retrofit
- Firebase (Auth, Realtime Database, Cloud Messaging, Crashlytics)
- minSdk 23 · targetSdk 35

## Login

Cada pessoa entra com e-mail e senha (Firebase Auth) e precisa confirmar o e-mail antes de usar o app. O app manda o ID token do Firebase em todas as chamadas ao backend (`Authorization: Bearer ...`), e o Realtime Database só libera chat e mapa para quem participa do churrasco.

Quem usava o app antes do login por e-mail pode pedir para recuperar o nome antigo na tela de cadastro. O pedido fica em análise até o administrador aprovar no backend.

## Como compilar

1. Abra o projeto no Android Studio (JDK 17).
2. Garanta que `app/google-services.json` aponta para o seu projeto Firebase, com o provedor **E-mail/senha** ativado em Authentication.
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
