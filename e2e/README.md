# Teste de ponta a ponta

Roda o app de verdade num emulador Android contra o backend local e os emuladores do Firebase. Nada toca no Firebase de produção. No GitHub Actions, o job `e2e` do workflow `Android` faz tudo isso sozinho em cada PR e push na `main`, e guarda as capturas de tela como artefato.

O que é verificado:
- **`drive.py`:**
  - cadastro com e-mail confirmado;
  - itens por categoria, com várias pessoas na mesma categoria e "Outro";
  - contas e Pix;
  - despesa digitada com vírgula;
  - chat sob as regras do banco;
  - localização que continua com a tela bloqueada;
  - edição pelo organizador;
  - compartilhar e aceitar o link de convite;
  - lembretes agendados;
  - pedido de amizade;
  - senha errada.
- **`legacy.py`:** pedido de recuperação de um nome antigo, aprovação pela tela do administrador e o churrasco antigo voltando para o dono.

`support.py` tem os atalhos: adb, leitura da tela pelo `uiautomator` e chamadas aos emuladores e à API.

## Como rodar na sua máquina

Precisa de:
- Node 22;
- Python 3;
- JDK 17;
- um emulador Android ligado;
- o [fcm-server](https://github.com/arthursucka/fcm-server) clonado ao lado deste repositório (ou `FCM_SERVER_DIR` apontando para ele), com `npm ci` feito.

```bash
cd e2e && npm install
npm run emulators        # terminal 1: Auth (9099) e Realtime Database (9000)
npm run mongo            # terminal 2: MongoDB descartável (replica set) na porta 27027
```

No terminal 3, suba o backend em modo de teste (na pasta do fcm-server):

```bash
CHURRASCO_LOCAL_TEST=1 GCLOUD_PROJECT=demo-churrasco \
FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 FIREBASE_DATABASE_EMULATOR_HOST=127.0.0.1:9000 \
FIREBASE_DATABASE_URL="http://127.0.0.1:9000/?ns=demo-churrasco" \
MONGO_URI="mongodb://127.0.0.1:27027/churrasco_test_e2e?replicaSet=testset" PORT=3000 node server.js
```

O `legacy.py` precisa de mais três variáveis no backend: `CHURRASCO_LEGACY_ONBOARDING_ENABLED=1 CHURRASCO_LEGACY_MIGRATION_WINDOW=1 CHURRASCO_MIGRATION_REVIEWER_UID=reviewer-uid-e2e`.

Depois, na raiz deste repositório:

```bash
./gradlew assembleLocalTest
python e2e/drive.py
python e2e/legacy.py
```

As capturas ficam em `e2e/shots/`. Cada roteiro limpa o Auth, o banco e o Mongo de teste antes de começar.

O login com Google não dá para testar aqui, porque exige uma conta Google real no aparelho.
