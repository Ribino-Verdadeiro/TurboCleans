<div align="center">
  <img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# ⚡ Turbo Clean — Otimizador Real & Premium para Android

O **Turbo Clean** é um aplicativo Android utilitário de otimização extremamente elegante, com um design cibernético premium inspirado em ficção científica futurista e cyberpunk. Ele realiza varreduras físicas de armazenamento, limpeza de lixo residual, compactação de bancos de dados locais via SQLite, e gerenciamento inteligente e em lote da galeria de fotos e vídeos.

---

## 📱 Como instalar o Turbo Clean no seu celular

Siga o guia passo a passo abaixo para compilar, gerar o instalador (APK) e rodar o Turbo Clean diretamente no seu aparelho físico de forma simples.

### 📋 Pré-requisitos
Para realizar esse processo, você precisará de:
* Um computador com o **[Android Studio](https://developer.android.com/studio)** instalado.
* Um cabo USB de boa qualidade para conectar seu celular ao computador.
* Seu celular Android.

---

### 1️⃣ Configurando o Celular (Modo Desenvolvedor)
Para que o seu computador consiga enviar o aplicativo direto para o celular, você precisa ativar o **Modo de Desenvolvedor** e a **Depuração USB**:

1. No seu celular, acesse **Configurações** > **Sobre o telefone**.
2. Procure por **Número da versão** (ou **Número de compilação**) e clique nele rapidamente **7 vezes seguidas**.
3. Uma mensagem aparecerá dizendo *"Você agora é um desenvolvedor"*.
4. Volte ao menu anterior de Configurações, entre em **Sistema** > **Opções do desenvolvedor** (ou pesquise por "Opções do desenvolvedor" na barra de buscas).
5. Localize a opção **Depuração USB** e ative-a.

---

### 2️⃣ Abrindo o Projeto no Android Studio
1. Abra o **Android Studio** no seu computador.
2. Na tela de boas-vindas, selecione **Open** (Abrir) e selecione a pasta raiz deste repositório (`TurboClean`).
3. Aguarde o Android Studio realizar a sincronização inicial e baixar as dependências do projeto (o Gradle fará isso automaticamente na primeira execução).

---

### 3️⃣ Como Gerar o Arquivo APK de Instalação

Se você quiser gerar o instalador `.apk` para instalar manualmente ou compartilhar com outras pessoas:

1. No menu superior do Android Studio, clique em **Build** (Compilar).
2. Selecione **Build Bundle(s) / APK(s)** e depois clique em **Build APK(s)**.
3. O Android Studio começará a compilar o projeto em segundo plano.
4. Assim que terminar, uma notificação aparecerá no canto inferior direito com um link **"locate"** (localizar). Clique nele!
5. A pasta com o arquivo **`app-debug.apk`** será aberta no seu computador.
6. Agora, basta copiar esse arquivo `.apk` para a memória do seu celular (através do cabo USB, enviando pelo Google Drive, Telegram ou WhatsApp) e clicar nele no celular para instalar.
   * *Nota: O celular poderá pedir permissão para "Instalar aplicativos de fontes desconhecidas". Basta conceder e prosseguir.*

---

### 4️⃣ Ou Rodando Diretamente pelo USB (Método mais rápido)
1. Conecte o celular ao computador usando o cabo USB.
2. No celular, se surgir uma solicitação perguntando *"Permitir depuração USB?"*, clique em **Permitir**.
3. No Android Studio, olhe para a barra de ferramentas superior. Você verá um seletor com o nome do seu aparelho de celular ativo.
4. Clique no botão de **Play verde (Run)** ou aperte `Shift + F10`.
5. O aplicativo será compilado e instalado automaticamente no seu celular e abrirá instantaneamente na tela!

---

## 🔒 Configurando as Permissões no Celular (Importante!)

Para que o Turbo Clean consiga fazer a limpeza física de verdade (e não apenas simular), ele necessita de permissões para ler o armazenamento. Quando abrir o app pela primeira vez:

1. **Acesso aos Arquivos**: O aplicativo exibirá um aviso explicativo na tela sobre a necessidade do **Acesso Completo ao Armazenamento**. Clique em conceder e ative a chavinha de autorização na tela de configurações do Android que abrir.
2. **Acesso à Galeria**: Ao entrar na ferramenta **Limpador de Galeria (Swipe)**, o sistema Android solicitará permissão de leitura de fotos e vídeos. Conceda a permissão para permitir que o app liste os arquivos mais pesados do seu aparelho de forma segura.

---

## 🎨 Funcionalidades do Aplicativo

* **Varredura Rápida (Junk scan)**: Encontra caches de sistema, arquivos `.tmp`, `.temp`, `.log`, instaladores `.apk` residuais e diretórios vazios físicos na pasta `/storage/emulated/0`, apagando-os permanentemente.
* **Compactação de Banco de Dados (SQLite Vacuum)**: Otimiza os índices de dados locais do próprio celular fazendo a limpeza interna e liberação de espaço do SQLite nativo.
* **Swipe Cleaner de Mídias (Estilo Tinder)**: Otimiza o armazenamento limpando sua galeria por lote. Arraste para a direita para **Manter** a foto/vídeo, e arraste para a esquerda para **Marcar para Apagar**. Ao fim, você confirma a deleção em lote unificado!
* **Deep Clean**: Encontra arquivos maiores que 15MB e arquivos duplicados reais para você escolher o que remover com segurança.
