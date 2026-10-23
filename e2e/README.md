# Teste de ponta a ponta

Roda o app de verdade num emulador Android contra o backend local e os emuladores do Firebase. Nada toca no Firebase de produção.

O que é verificado:
- `drive.py`: criar conta, confirmar e-mail, escolher nome, chat (com as regras do banco), localização com sessão, sair, entrar de novo, senha errada e nome já usado
- `legacy.py`: pedido de recuperação de nome antigo, aprovação pela tela do administrador e entrada com o nome recuperado

## Como rodar

Precisa de Node 22, Python 3, JDK 17, um emulador Android ligado e o [fcm-server](https://github.com/arthursucka/fcm-server) clonado ao lado deste repositório (ou `FCM_SERVER_DIR` apontando para ele, com `npm ci` feito).

```bash
cd e2e && npm install
npm run emulators        # terminal 1: Auth (9099) e Realtime Database (9000)
npm run mongo            # terminal 2: MongoDB descartável na porta 27027
```

Terminal 3, o backend em modo de teste (na pasta do fcm-server):

```bash
CHURRASCO_LOCAL_TEST=1 GCLOUD_PROJECT=demo-churrasco \
FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 FIREBASE_DATABASE_EMULATOR_HOST=127.0.0.1:9000 \
FIREBASE_DATABASE_URL="http://127.0.0.1:9000/?ns=demo-churrasco" \
MONGO_URI="mongodb://127.0.0.1:27027/churrasco_test_e2e?replicaSet=testset" PORT=3000 node server.js
```

Para o `legacy.py`, inicie o backend com mais estas variáveis: `CHURRASCO_LEGACY_ONBOARDING_ENABLED=1 CHURRASCO_LEGACY_MIGRATION_WINDOW=1 CHURRASCO_MIGRATION_REVIEWER_UID=reviewer-uid-e2e`.

Depois, na raiz deste repositório:

```bash
./gradlew assembleLocalTest
python e2e/drive.py
python e2e/legacy.py
```

As capturas de tela ficam em `e2e/shots/`. Cada roteiro limpa o Auth, o banco e o Mongo de teste antes de começar.
