# Churrasco 🍖

App Android para organizar churrascos com os amigos.

- **Convites:** por link, enviado por qualquer app, ou direto para a lista de amigos.
- **Itens:** cada um diz o que leva, com detalhes como corte, kg e tempero. Várias pessoas podem levar a mesma categoria, e "Outro" aceita texto livre.
- **Contas:** divisão das despesas com Pix "copia e cola" e QR Code.
- **No dia:** mapa ao vivo que funciona com a tela bloqueada, botões de Maps e Waze, chat e lembretes.

Backend: [fcm-server](https://github.com/arthursucka/fcm-server).

## Download

Baixe o APK mais recente na aba **[Releases](https://github.com/arthursucka/Churrasco/releases/latest)**. Cada Release também traz o `.aab` para a Play Store (veja [docs/play-store.md](docs/play-store.md)).

## Stack

- Kotlin + Android Views, montadas com um kit próprio em `ui/` (escala de 8 pt)
- Corrotinas, ViewModel e Retrofit com `suspend`, numa camada `data/` sem callbacks
- Firebase: Auth com e-mail/senha e Google, Realtime Database, Cloud Messaging e Crashlytics
- osmdroid (mapa nativo do OpenStreetMap), Fused Location e um serviço em primeiro plano para a localização
- minSdk 23 · targetSdk 35 · R8 ligado: o APK de release tem cerca de 3 MB

## Estrutura

```
app/src/main/java/com/longynus/churrasco/
  data/        API v2 (ChurrascoApi), modelos e ChurrascoRepository
  ui/          Kit de componentes e ScreenActivity (base das telas em código)
  details/     ViewModel da tela do churrasco e formatação de dinheiro
  items/       Catálogo de categorias e a tela "O que você vai levar?"
  friends/     Amigos e perfil (nome e chave Pix)
  invites/     Link de convite (App Link) e compartilhamento
  location/    Serviço de localização em primeiro plano
  reminders/   Lembretes locais 24 h e 2 h antes
  pix/         Código Pix (BR Code com CRC16)
e2e/           Teste de ponta a ponta no emulador (ver e2e/README.md)
docs/          Política de privacidade e guia da Play Store
fastlane/      Textos da loja
```

## Login

Cada pessoa entra com e-mail e senha, que exigem confirmação do e-mail, ou com a conta Google. O app manda o ID token do Firebase em todas as chamadas (`Authorization: Bearer ...`) e a versão (`X-App-Version`); o backend recusa versões antigas com "Atualize o app". O Realtime Database só libera chat e mapa para quem participa do churrasco.

Quem usava o app antes do login por e-mail pode pedir para recuperar o nome antigo na tela de cadastro. O administrador aprova pela tela "Revisar recuperação de contas".

## Como compilar

1. Abra o projeto no Android Studio (JDK 17).
2. Garanta que `app/google-services.json` aponta para o seu projeto Firebase, com os provedores **E-mail/senha** e **Google** ativados em Authentication e os SHA-1/SHA-256 das suas chaves cadastrados.
3. Rode:

```bash
./gradlew assembleDebug
```

Testes unitários: `./gradlew testDebugUnitTest`.

## Release

O CI publica sozinho. Basta enviar uma tag com a versão:

```bash
git tag -a v2.1 -m "Churrasco v2.1" && git push origin v2.1
```

O workflow `Android` roda os testes, gera o APK e o AAB assinados (com a keystore guardada nos *secrets* do repositório) e cria a Release.

Para assinar na sua máquina, crie um `keystore.properties` na raiz (ele é ignorado pelo git):

```properties
storeFile=caminho/para/sua.jks
storePassword=...
keyAlias=...
keyPassword=...
```

## Regras do Firebase

As regras do Realtime Database ficam em `firebase-database-rules.json` e são publicadas com:

```bash
firebase deploy --only database
```
