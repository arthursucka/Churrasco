# Publicar na Play Store

Tudo o que dá para preparar sem a conta de desenvolvedor já está no repositório. Falta só o que exige a conta (taxa única de US$ 25).

## O que já está pronto

| Item | Onde |
|---|---|
| App Bundle (AAB) assinado | Anexo de cada Release (`manda-churras-vX.Y.aab`), gerado pelo CI a cada tag `vX.Y` |
| `versionCode` crescente | Sai da tag: `v2.0` → 2000, `v2.1` → 2010, `v2.1.1` → 2011 |
| `targetSdk` 35 | `app/build.gradle.kts` |
| Título, descrição curta e completa, novidades | `fastlane/metadata/android/pt-BR/` (dentro dos limites de 30/80/4000/500 caracteres) |
| Política de privacidade | `docs/privacidade.html`, publicada em https://arthursucka.github.io/Churrasco/privacidade.html |
| Capturas de tela | `fastlane/metadata/android/pt-BR/images/phoneScreenshots/` (6 telas emolduradas, 1080 × 1920). Para refazer: `e2e/tour.py` e o roteiro de molduras |
| Arte de destaque | `fastlane/metadata/android/pt-BR/images/featureGraphic.png` (1024 × 500). Troque pela arte LOJA-01 quando ela for gerada |

## Passo a passo

1. Crie a conta em https://play.google.com/console (US$ 25, pagamento único) e confirme a identidade.
2. **Criar app** → nome "Manda Churras", idioma Português (Brasil), App, Gratuito.
3. Em **Configurar o app**, preencha com as respostas abaixo.
4. **Testes → Teste interno → Criar versão**: envie o `.aab` da última Release. A Play pede para ativar a **Assinatura de apps do Google Play**; aceite (a chave do CI vira a "chave de upload").
5. Depois de ativar, copie em **Configuração → Integridade do app** o **SHA-1 e o SHA-256 da chave de assinatura do app** e cadastre no Firebase (Configurações do projeto → seu app Android → Adicionar impressão digital). Sem isso, o login com Google não funciona em quem instalar pela Play.
6. Coloque o mesmo SHA-256 no Render, na variável `ANDROID_CERT_SHA256` (separado por vírgula do atual), para os links de convite abrirem direto no app.
7. Convide os amigos como testadores internos, teste e, quando quiser, promova para Produção.

## Respostas para o formulário

**Acesso ao app**: todas as funções exigem login. Crie uma conta de teste (e-mail e senha) e informe na Play.

**Anúncios**: o app não tem anúncios.

**Classificação do conteúdo**: Comunicação/Social; tem chat entre usuários e compartilhamento de localização entre usuários que se conhecem; sem conteúdo violento, sexual ou de apostas.

**Público-alvo**: 18 anos ou mais (o app fala de bebidas alcoólicas nos itens).

**Segurança dos dados**:

| Dado | Coletado | Compartilhado | Para quê | Opcional |
|---|---|---|---|---|
| E-mail | Sim | Não | Gerenciar conta, encontrar amigos | Não |
| Nome | Sim | Não | Funcionalidade do app | Não |
| Localização precisa | Sim | Não | Funcionalidade (mapa ao vivo) | Sim |
| Mensagens no app | Sim | Não | Funcionalidade (chat) | Sim |
| Informações financeiras (chave Pix e despesas) | Sim | Não | Funcionalidade (divisão de contas) | Sim |
| IDs do dispositivo (token de notificação) | Sim | Não | Notificações | Não |
| Registros de falhas | Sim | Não | Análise | Não |

Os dados são criptografados em trânsito (HTTPS) e o usuário pode pedir a exclusão pelo e-mail da política de privacidade.

**Serviço em primeiro plano (localização)**: declare o tipo `location`. Texto sugerido:

> Quando a pessoa toca em "Estou a caminho" na tela de um churrasco, o app compartilha a localização dela com os amigos que confirmaram presença, para todos verem quem está chegando. O compartilhamento só começa por ação da pessoa, com o app aberto, a partir de 1 hora antes do evento; dura no máximo 2 horas; mostra uma notificação fixa com o botão "Parar" o tempo todo; e continua com a tela bloqueada porque a pessoa normalmente está dirigindo. O app não pede localização em segundo plano (ACCESS_BACKGROUND_LOCATION).

Grave um vídeo curto (a Play pede) mostrando: abrir um churrasco → "Estou a caminho" → notificação fixa → "Parar".

**Permissões sensíveis**: só `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION` (durante o uso), `POST_NOTIFICATIONS` e `FOREGROUND_SERVICE_LOCATION`. Não usa alarmes exatos nem localização em segundo plano.
