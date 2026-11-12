# EuPlantei

Aplicativo Android **EuPlantei** (`br.com.jabsdev.euplantei`) para diagnóstico e cuidado de plantas com **visão computacional, base de conhecimento e LLM rodando 100% no aparelho**. A foto é classificada localmente, o clima do local é consultado no Open-Meteo (sem chave), e um modelo de linguagem GGUF gera um plano de cuidado em português do Brasil.

- **Alvo principal:** Samsung Galaxy S22 (8 GB RAM, arm64-v8a)
- **minSdk 28 · targetSdk 35 · compileSdk 35**
- **ABI:** arm64-v8a
- **APK de release:** ~48 MB (o GGUF nunca é embutido; inclui os dois modelos TFLite)
- **Offline:** visão, LLM, base local e cálculo solar funcionam sem internet; clima usa cache ou entrada manual
- **Privacidade:** fotos e consultas ficam no armazenamento do app; não há analytics, anúncios ou tracking
- **Download em segundo plano:** foreground service `dataSync` + WakeLock + notificação de progresso; o download não para quando a tela apaga

---

## 1. Funcionalidades

- Captura com CameraX (troca de câmera, flash, guia de enquadramento e dica de luz).
- Galeria via **Photo Picker** (`ActivityResultContracts.PickVisualMedia`), sem permissões de armazenamento.
- Classificação híbrida: modelo de **47 plantas de interior** + modelo PlantVillage de **38 classes de cultura/doença**.
- Confirmação da espécie quando há candidatos de planta de interior; confiança ≥65% automática para culturas e 40–64% confirmação.
- Para plantas de interior, etapa de sintomas (folhas amarelas, murcha, manchas etc.) antes do conselho.
- Clima Open-Meteo com localização aproximada, cache persistente, fallback offline e entrada manual.
- `SolarCalculator` NOAA offline para nascer/pôr do sol, duração do dia e posição solar.
- Mini-RAG local com **67 perfis** de plantas e **135 doenças** (30 ornamentais brasileiras, 14 culturas do PlantVillage e 23 plantas de interior do modelo híbrido).
- LLM GGUF via llama.cpp/JNI, chat template do próprio modelo, streaming token a token e cancelamento real.
- Ficha de cuidados da espécie acima do conselho: luz, rega, poda, adubação, temperatura, solo e toxicidade para pets.
- Proteção térmica: aviso a 40 °C e bloqueio de nova geração a 43 °C.
- Modelo carregado uma única vez, ocioso por 120 s e descarregado; nunca recarrega a cada consulta.
- Histórico Room com busca, filtro por planta, detalhe, exclusão com “Desfazer” (5 s) e exportação `.txt`.
- Compartilhamento de texto + imagem via FileProvider.
- Temas claro/escuro/sistema, unidade °C/°F, fonte ampliada, edge-to-edge e transições animadas.
- Rascunho persistente em DataStore: rotação, morte de processo e retomada pelo cartão “Consulta em andamento” na Home.
- **Jardim virtual:** adiciona a planta do resultado, registra “Reguei” e guarda fotos de acompanhamento da mesma planta.
- **Frequência de rega recomendada pelo app** para as 67 espécies (`intervalo_rega_dias` na base local), usada como padrão ao adicionar ao jardim, no detalhe da planta e no lembrete diário. O usuário pode ajustar.
- **Lembretes diários com clima:** WorkManager notifica às 9h; em dia de chuva pede para proteger as plantas, em calor forte lembra de conferir a umidade e borrifar folhas, e em ar seco/frio/vento dá a orientação correspondente. Sem rede, os lembretes de rega continuam.
- Room **v2** com migração `1→2` que preserva o histórico de consultas existente.
- **Atualização pelo GitHub Releases:** o app consulta a última release, compara `versionCode`, baixa o APK e abre o instalador do Android (com verificação SHA-256).

---

## 2. Como compilar

### Requisitos

- JDK 17 ou 21
- Android SDK 35
- NDK **28.2.13676358**
- CMake **3.22.1** (instalado pelo SDK)
- Gradle Wrapper 8.9 (incluído)
- Aproximadamente 4 GB livres para o build nativo

Configure o SDK em `local.properties`:

```properties
sdk.dir=/caminho/para/Android/Sdk
```

Build e testes:

```bash
export JAVA_HOME=/caminho/para/jdk
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Release:

```bash
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

Para emulador x86_64 (somente desenvolvimento):

```bash
./gradlew assembleDebug -PeuplanteiAbis=x86_64
./gradlew connectedDebugAndroidTest -PeuplanteiAbis=x86_64
```

O padrão é **arm64-v8a**. O APK de debug também é minificado (R8) para permanecer abaixo de 60 MB; o release fica em ~48 MB e o debug em ~56 MB.

> O release local está assinado com a **chave de debug** para permitir instalação por sideload. Para publicar, crie um keystore próprio e altere `signingConfigs` em `app/build.gradle.kts`. Nunca distribua o APK assinado com a chave de debug.

---

## 3. Modelo de linguagem (GGUF)

### Download padrão

O app **não** inclui o GGUF. O padrão é Qwen 2.5 1.5B Instruct Q4_K_M:

- URL: `https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf`
- Tamanho: 1.117.320.736 bytes (~1,1 GB)
- SHA-256: `6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e`

O download roda em um **foreground service** (`ModelDownloadService`) com notificação de progresso, permissão `POST_NOTIFICATIONS` solicitada ao usuário e WakeLock parcial. Assim ele continua com a tela apagada e o app em segundo plano. As permissões de manifesto adicionais são `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS` e `WAKE_LOCK`.

O download:

- usa HTTP Range e retoma de `filesDir/models/download.part`;
- verifica SHA-256 quando configurado;
- valida o cabeçalho `GGUF`;
- mostra progresso em % e MB;
- pode ser cancelado (o progresso é preservado);
- grava o modelo ativo em `filesDir/models/`.

### Configurar URL/SHA em tempo de build

```bash
./gradlew assembleDebug \
  -PeuplanteiModelUrl="https://exemplo.com/meu-modelo.gguf" \
  -PeuplanteiModelSha256="<64 caracteres hexadecimais>"
```

Os valores viram `BuildConfig.MODEL_URL` e `BuildConfig.MODEL_SHA256`. A URL também pode ser alterada em **Configurações → Modelo de IA local → Fonte do modelo**. O SHA é opcional; se preenchido, precisa ter 64 caracteres hexadecimais.

### Importar um GGUF fine-tuned

1. Copie o arquivo `.gguf` para o celular (Downloads, Drive etc.).
2. Abra **Configurações → Modelo de IA local → Trocar modelo**.
3. Selecione o arquivo no seletor de documentos.
4. O app valida a extensão e o magic `GGUF`, copia para `filesDir/models/` e ativa o modelo.

Modelos menores (por exemplo, Qwen 2.5 0.5B Q4_K_M) funcionam para testes, com qualidade de resposta inferior.

---

## 4. Modelo de visão (TFLite)

Arquivos:

```text
app/src/main/assets/models/plant_model.tflite           (2,5 MB, 38 classes de cultura/doença)
app/src/main/assets/models/labels.txt                   (rótulos PlantVillage)
app/src/main/assets/models/labels_ptbr.json             (nomes PT-BR)
app/src/main/assets/models/houseplant_model.tflite      (29 MB, 47 plantas de interior)
app/src/main/assets/models/houseplant_labels.txt        (rótulos do modelo de interior)
app/src/main/assets/models/houseplant_labels_ptbr.json  (nomes PT-BR e perfis)
```

Existem dois modelos:

1. **PlantVillage** (MobileNetV2 dynamic-range int8): 38 classes de cultura/doença.
   - SHA-256: `38a7fd305b2272a502b9a9be63fc78d5834bc95d8571e6fc3459ad3385982705`
   - Origem demo: `https://raw.githubusercontent.com/priyanshu-1git/crop_scan/main/cropapp/assets/model.tflite`
   - Entrada `[1,224,224,3]` float32 em **[0,1]**, saída `[1,38]` softmax.
   - Pré-processamento [0,1] verificado na prática.

2. **Houseplant Species** (EfficientNetB4): 47 plantas de interior, 29 MB.
   - Origem: `https://huggingface.co/datasets/kakasher/house-plant-species` e modelo TFLite público `AlyModrik41/House-Plants-Classification-TFLite-Model`.
   - Entrada `[1,224,224,3]` float32 em **[0,255]** (o modelo já tem `Rescaling(1/255)` interno), saída `[1,47]` softmax.
   - Dataset com 14.790 imagens e 47 classes; o card do dataset pede uso pessoal e a licença declarada é CC BY 3.0. Não comercializar sem revisar os direitos das imagens.
   - O `HybridVisionClassifier` roda os dois modelos. Se houver candidato de planta de interior, a UI pede confirmação da espécie; depois pede os sintomas observados (folhas amarelas, murcha, manchas etc.) antes do LLM. Isso evita o caso de um lírio ser diagnosticado como milho.

Para substituir:

1. Gere/baixe um `.tflite` com a mesma interface (224×224, 38 classes, saída softmax) ou ajuste `PlantClassifier`.
2. Substitua `plant_model.tflite`.
3. Atualize `labels.txt` na mesma ordem das saídas.
4. Atualize `labels_ptbr.json` (chaves normalizadas sem acento) com `species`, `condition`, `speciesId` e `healthy`.
5. Rode `./gradlew testDebugUnitTest` e valide fotos reais.
6. Se alterar a normalização, ajuste `createInputBuffer` em `PlantClassifier.kt`.

**Limite importante:** o PlantVillage cobre 38 classes de 14 culturas, não todas as plantas ornamentais. Fotos muito diferentes ou de espécies fora dessas classes podem gerar confiança alta e classe errada. A ficha de cuidados só aparece quando a espécie identificada existe na base local; use o fluxo manual quando a foto não corresponder. Para produção, treine um modelo com fotos reais e inclua uma etapa de rejeição de imagens não vegetais.

---

## 5. Base de conhecimento (`plants.json`)

`app/src/main/assets/knowledge/plants.json` contém **67 perfis** e **135 doenças**:

- 30 ornamentais comuns no Brasil: rosa, phalaenopsis, antúrio, samambaia, suculentas, violeta, lírio, jasmim, hortênsia, espada-de-são-jorge, jiboia, comigo-ninguém-pode, ráfis, ficus, gerânio, cravo, lavanda, alecrim, manjericão, hortelã, zamioculca, peperômia, kalanchoe, begônia, clorofito, maranta, pata-de-elefante, bonsai, girassol e tulipa;
- 14 culturas cobertas pelo classificador: tomateiro, macieira, batateira, videira, milho, pimentão, morangueiro, aboboreira, pessegueiro, laranjeira, mirtileiro, cerejeira, framboeseira e soja;
- 23 plantas de interior do modelo híbrido: areca, ave-do-paraíso, calateia, aspidistra, aglaonema, pilea, crisântemo, dracaena, hera, lírio-do-vale, narciso, jacinto, árvore-do-dinheiro, monstera, lírio-da-paz, poinsétia, pingo-de-ouro, ponytail, sagu, schefflera, tradescântia, dionaea e yucca.

Schema por planta:

```json
{
  "id": "rosa",
  "nomes": ["Rosa", "Roseira", "Rosa spp."],
  "luz": "...",
  "rega_por_faixa_umidade": "...",
  "faixa_temperatura": "...",
  "solo": "...",
  "poda": "...",
  "adubacao": "...",
  "doencas_comuns": [
    { "nome": "...", "sintomas": "...", "manejo": "..." }
  ],
  "toxicidade_pet": "..."
}
```

`KnowledgeRetriever` normaliza acentos, casa espécie e palavras do sintoma, retorna no máximo 2 perfis e 3 doenças e injeta o texto no prompt. Sem correspondência, o prompt recebe “sem dados específicos para esta espécie; use conhecimento geral com cautela”.

O conteúdo é conservador: prioriza manejo cultural/biológico, não prescreve agrotóxicos nem doses. Cada perfil inclui **poda** e **adubação**, usados tanto na ficha exibida no resultado quanto no texto injetado no prompt.

### Sobre o dataset da Embrapa

O link `br-llm-data/embrapa-corpus-cs` é um **corpus textual** (Parquet, ~1,2 GB) construído a partir de publicações da Embrapa — não é um dataset de imagens e, portanto, **não serve para treinar o classificador TFLite**. A licença é `other` e o repositório é **gated** (requer aprovação no Hugging Face). Cada linha também carrega campos `license`/`rights` dos documentos de origem.

Ele pode ser usado para **RAG local** ou **fine-tuning/QLoRA do LLM** (Qwen), mas não para visão. Para visão, o dataset público de imagens da Embrapa é a **PDDB/Digipathos** (DOI `10.48432/XA1OVL`), cuja licença declarada é **CC BY-NC 4.0**. Como o app não será comercializado nesta etapa, o uso não comercial é compatível com essa licença, mas o corpus/documentos não devem ser redistribuídos dentro do APK sem verificar os direitos de cada publicação.

---

## 6. Identidade e assinatura

- **Nome:** EuPlantei
- **applicationId/namespace:** `br.com.jabsdev.euplantei`
- **Não publicado na Play Store nesta etapa**
- Release local assinado com a chave de debug para sideload. Para publicação futura, troque o keystore.

## 7. Arquitetura

```text
app/src/main/java/br/com/jabsdev/euplantei/
├── AppContainer.kt                 # DI manual, escopo de aplicação
├── EuPlanteiApplication.kt      # onTrimMemory → descarrega LLM
├── ui/
│   ├── MainActivity.kt             # edge-to-edge, tema, NavHost
│   ├── navigation/                 # rotas, bottom bar, animações
│   ├── screens/{onboarding,home,camera,result,garden,history,settings}
│   ├── components/                 # badge, markdown, skeleton, chips
│   └── theme/                      # cores botânicas, tipografia, shapes
├── data/
│   ├── db/                         # Room v2: consultas, jardim e fotos
│   ├── prefs/                      # DataStore: tema, unidade, modelo, clima, rascunho
│   └── repo/                       # Weather, Knowledge, Consultation, Model, Garden, Update
├── domain/
│   ├── PromptBuilder.kt
│   ├── SolarCalculator.kt
│   ├── WeatherCodeMapper.kt
│   ├── KnowledgeRetriever.kt
│   ├── GardenWeatherAdvisor.kt
│   ├── GardenWatering.kt
│   ├── LabelParser.kt
│   └── model/                      # data classes serializáveis
├── ml/
│   ├── PlantClassifier.kt          # TFLite 224×224, 4 threads
│   ├── VisionClassifier.kt         # interface para fake
│   ├── LlmEngine.kt                # interface + implementação JNI
│   ├── LlmManager.kt               # lifecycle, fila, térmica, 15 s
│   └── NativeLlamaBridge.kt
├── service/
│   ├── ModelDownloadService.kt
│   └── GardenReminders.kt          # WorkManager + notificação diária
├── util/
│   ├── ImageUtils.kt
│   ├── UpdateInstaller.kt          # PackageInstaller/FileProvider
│   ├── ThermalMonitor.kt
│   └── NetworkMonitor.kt
└── jni/                            # CMake + llama_bridge.cpp (na verdade app/src/main/cpp)

app/src/main/cpp/
├── CMakeLists.txt
├── llama_bridge.cpp
└── llama.cpp/                      # cópia fixada do upstream (commit e3de71522)
```

- **MVVM:** `ViewModel` + `StateFlow` + corrotinas; telas coletam com `collectAsStateWithLifecycle`.
- **Sem Hilt:** `AppContainer` manual, criado em `Application`.
- **Room:** histórico e jardim; **DataStore:** preferências e rascunho da consulta.
- **WorkManager:** lembrete diário do jardim às 9h, com clima do Open-Meteo quando disponível.
- **Testabilidade:** `VisionClassifier` e `LlmEngine` são interfaces; o teste instrumentado usa fakes.

### Estado da consulta

`ConsultationStage`: `Idle`, `Classifying`, `AwaitingConfirmation`, `AwaitingManual`, `FetchingWeather`, `Generating(partial)`, `Success`, `Error(failure, partial)`.

O rascunho é salvo em DataStore nas transições e a cada 2 s durante o streaming. Ao reabrir, a Home mostra “Consulta em andamento” e o Resultado restaura o estado.

---

## 8. LLM local e JNI

`llama.cpp` está vendorizado em `app/src/main/cpp/llama.cpp` (commit `e3de71522`, licença MIT) e compilado com CMake para arm64-v8a. O build usa:

- `n_ctx = 4096`, `n_threads = 4`, `n_batch = 256`
- `mmap`, sem `mlock`
- chat template obtido por `llama_model_chat_template` + `llama_chat_apply_template` (model-agnostic)
- sampling: temperature 0.3, top_p 0.9, top_k 40, repeat_penalty 1.1, max 512 tokens
- callback JNI token a token; `cancel` é um `std::atomic` checado a cada token
- `unload` após 120 s ocioso ou `onTrimMemory(RUNNING_CRITICAL)`

A ponte expõe `nativeLoadModel`, `nativeGenerate`, `nativeCancel`, `nativeUnload` e `nativeIsLoaded` em `NativeLlamaBridge`.

### Proteção térmica

`ThermalMonitor` lê `ACTION_BATTERY_CHANGED` (sticky):

- **≥40 °C:** banner âmbar;
- **≥43 °C:** nova geração bloqueada com mensagem e opção de tentar depois;
- intervalo mínimo de 15 s entre gerações;
- `Mutex.tryLock` garante uma geração por vez e descarta cliques duplicados.

---

## 9. Privacidade

- Nenhum dado é enviado a servidores do EuPlantei.
- Sem anúncios, analytics ou tracking.
- Fotos ficam em `cacheDir/consultas` durante o fluxo e são copiadas para `filesDir/consultas` ao salvar no histórico.
- Compartilhamento só por ação explícita (FileProvider).
- Open-Meteo recebe somente latitude/longitude arredondadas para **2 casas decimais**.
- O geocodificador do Android pode ser usado para transformar coordenadas no nome da cidade; isso é um serviço do sistema operacional e pode usar rede. O usuário pode **desativar o geocodificador em Configurações → Privacidade → Nome da cidade**; nesse caso o app mostra “Localização aproximada”. O padrão é ativado, mas o controle fica sempre visível.
- O download do GGUF vai direto para a URL configurada.
- `allowBackup=false`; modelos, consultas e banco são excluídos de backup/transferência.

---

## 10. Matriz offline

| Recurso | Sem internet |
|---|---|
| Visão TFLite | funciona |
| LLM local | funciona (após o modelo estar no dispositivo) |
| Base de conhecimento | funciona |
| `SolarCalculator` | funciona |
| Clima | último cache com aviso, ou entrada manual |
| Download de modelo | indisponível com mensagem clara; importação local funciona |

A consulta **nunca bloqueia** por falta de clima: se não houver localização, cache ou rede, o prompt informa que o clima não foi informado e o LLM recebe a instrução de dar conselho geral.

---

## 11. Testes

### Unitários (30)

```bash
./gradlew testDebugUnitTest
```

Cobrem:

- `PromptBuilder` com todos os campos e fallbacks;
- `WeatherCodeMapper`;
- `SolarCalculator` (São Paulo, faixas plausíveis);
- `KnowledgeRetriever` com/sem acento, sem match, limite de 2 perfis/3 doenças e JSON real;
- parser de labels PlantVillage e regras 65/40%;
- `GardenWeatherAdvisor` (chuva, calor, ar seco, frio, vento e clima ameno);
- status de rega por dia do calendário;
- presença de `intervalo_rega_dias` válido nos 67 perfis;
- leitura do `update.json` e comparação de `versionCode`.

### Instrumentado

```bash
./gradlew connectedDebugAndroidTest -PeuplanteiAbis=x86_64
```

4 testes em emulador: `FakeFlowInstrumentedTest` usa `FakeVisionClassifier` e `FakeLlmEngine`, salva no Room, lista, exclui e desfaz; `GardenDaoInstrumentedTest` cobre planta, rega, foto e exclusão em cascata; `GardenMigrationTest` abre um banco v1 real e valida a migração `1→2`; `GardenNotificationInstrumentedTest` publica a notificação diária com aviso de chuva.

### Checklist manual executado (emulador x86_64, API 35)

| Item | Resultado |
|---|---|
| `assembleDebug` | ✅ |
| `testDebugUnitTest` | ✅ 30 testes |
| `connectedDebugAndroidTest` | ✅ 4 testes |
| `assembleRelease` + R8 | ✅ |
| APK debug / release | ✅ 56 MB / 48 MB |
| Importação de GGUF (0,5 B) | ✅ |
| Download de 1,1 GB, cancelamento e retomada por Range | ✅ |
| Download com tela apagada (foreground service + WakeLock) | ✅ (partição cresceu de 37 MB para 862 MB e concluiu com a tela apagada) |
| Verificação SHA-256 do download padrão | ✅ |
| Troca automática para o modelo baixado | ✅ |
| Câmera + permissão + captura | ✅ (câmera virtual do emulador) |
| Photo Picker | ✅ |
| Classificação PlantVillage | ✅ (foto de requeima do tomateiro → 92%) |
| Classificação híbrida em foto de lírio | ✅ (Lírio 71% + confirmação da espécie; morango apareceu como candidato de cultura) |
| Etapa de sintomas para planta de interior | ✅ |
| Geração 0,5 B e 1,5 B | ✅ |
| Cancelamento real no JNI | ✅ (ícone de parar e botão no card) |
| Bloqueio térmico a 44 °C | ✅ |
| Permissão de localização e fallback por negação | ✅ |
| Clima manual (cidade/temp/umidade/chuva) e regeneração | ✅ |
| Morte de processo e retomada do rascunho pela Home | ✅ |
| Exclusão no histórico com Snackbar “Desfazer” | ✅ |
| Tema escuro | ✅ |
| Fonte ampliada (1,3×) na Home | ✅ |
| Release R8 instalado e fluxo de geração | ✅ |
| Jardim: adicionar, regar, foto de evolução e excluir | ✅ (fluxo completo no emulador) |
| Migração Room v1→v2 preservando consulta | ✅ (teste instrumentado com banco v1 real) |
| Canal e agendamento do lembrete diário às 9h | ✅ (`dumpsys jobscheduler` e `dumpsys notification`) |
| Aviso de clima no jardim (chuva, calor, ar seco) | ✅ (regras unitárias + notificação com chuva) |
| Atualização via GitHub Releases | ✅ (update.json, comparação de versionCode, download + SHA-256 e instalador) |

Itens que dependem de hardware real e **não** foram medidos aqui: temperatura real do S22, 10 consultas consecutivas com monitoramento de memória, rotação durante geração no aparelho, clima real com GPS físico e compartilhamento em apps externos.

---

## 12. Limitações conhecidas

1. **Modelo de visão:** 38 classes de culturas do PlantVillage, treinado com imagens de laboratório. A confiança pode ser alta em imagens fora da distribuição. Para produção, treine com fotos reais e adicione um detector/rejeição de vegetação.
2. **Qualidade do LLM:** o modelo padrão de 1,5 B é pequeno. Em aparelhos com menos RAM, importe um GGUF menor; respostas podem variar. O app não faz fine-tuning.
3. **Térmica:** a leitura é da bateria, não da CPU/GPU. Os limiares (40/43 °C) são conservadores e devem ser calibrados em aparelhos reais.
4. **Geocodificação:** o `Geocoder` do Android pode usar rede do sistema; a consulta ao Open-Meteo em si usa apenas coordenadas arredondadas.
5. **Licença do modelo de visão:** o arquivo embutido foi obtido de um repositório público com finalidade educativa. Verifique a licença do PlantVillage e do repositório de origem antes de distribuir comercialmente; substitua por um modelo com licença compatível. A PDDB/Digipathos da Embrapa é CC BY-NC 4.0, não Apache 2.0; não foi usada neste build. O corpus textual `br-llm-data/embrapa-corpus-cs` é gated, tem licença `other` e não é um dataset de imagens.
6. **Assinatura:** o release local usa chave de debug. Troque antes de publicar.
7. **Android 15/target 35:** o app foi testado em emulador API 35; o S22 físico deve ser validado.
8. **Exportação:** o arquivo `.txt` é gerado via `ACTION_CREATE_DOCUMENT`; alguns gerenciadores de arquivos podem não estar disponíveis em todos os aparelhos.

---

## 13. Licenças e créditos

- **llama.cpp / ggml** — MIT. Cópia vendorizada em `app/src/main/cpp/llama.cpp` (commit `e3de71522`).
- **TensorFlow Lite** — Apache 2.0.
- **MobileNetV2 / PlantVillage** — modelo educativo; ver limitação de licença acima.
- **Qwen 2.5** — Apache 2.0. Baixado separadamente; **não** incluso no APK.
- **Open-Meteo** — dados meteorológicos gratuitos, sem chave.
- **Material Icons** — Apache 2.0.
- **AndroidX / Compose / Room / CameraX / Coil / OkHttp** — licenças Apache 2.0 / BSD conforme os projetos.

Consulte também `THIRD_PARTY_NOTICES.md` e `app/src/main/assets/models/NOTICE.md`.

---

## 14. Estrutura de dados do histórico

`ConsultationEntity` (Room):

- `id`, `createdAt`, `imagePath`
- `species`, `condition`, `confidencePercent`, `manual`
- `symptom`, `question`, `city`
- `weatherJson` (snapshot do clima)
- `advice` (markdown do LLM)

O DAO expõe `observeAll`, `observeById`, `insert`, `deleteById`, `clear`. A busca e o filtro por espécie são feitos em memória sobre o Flow do Room, o que é suficiente para o volume esperado de consultas pessoais.


---

## 15. Atualizações via GitHub Releases

Repositório: **https://github.com/JabsDev/EuPlantei** (público, para que o app possa baixar o APK sem embutir token).

Fluxo no app:

1. Ao abrir a Home (no máximo a cada 6 horas) ou ao tocar em **Configurações → Atualizações → Verificar atualizações**, o app busca:
   `https://github.com/JabsDev/EuPlantei/releases/latest/download/update.json`
2. Compara o `versionCode` do JSON com `BuildConfig.VERSION_CODE`.
3. Se for maior, mostra o cartão de atualização com a versão e as notas.
4. Ao tocar em **Baixar e instalar**, baixa o APK, confere o SHA-256 e abre o instalador do Android.
5. Na primeira vez, o Android pede para permitir a instalação de apps desta fonte; o app abre essa tela automaticamente.

Formato do `update.json` (asset de cada release):

```json
{
  "versionCode": 3,
  "versionName": "1.1.1",
  "apkUrl": "https://github.com/JabsDev/EuPlantei/releases/download/v1.1.1/EuPlantei-1.1.1.apk",
  "sha256": "…",
  "notes": "Notas da versão"
}
```

Publicando uma nova versão:

```bash
# 1. Suba versionCode e versionName em app/build.gradle.kts
# 2. Rode o script (lê o token de ~/.config/opencode/gh_token ou GH_TOKEN)
./scripts/publish_release.sh "Notas da versão"
```

O script compila o release, calcula o SHA-256, cria a tag `v<versionName>`, envia o APK e o `update.json` via `gh release`. O APK do app e o release precisam usar a **mesma chave de assinatura**; neste projeto o release local usa a chave de debug, então atualizações entre builds desta máquina funcionam. Antes de distribuir para outras pessoas, troque para um keystore próprio e mantenha a mesma chave em todas as versões.
