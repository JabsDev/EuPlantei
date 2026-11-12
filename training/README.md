# Treino do classificador de espécies do EuPlantei

Este diretório é independente do app Android. Ele baixa as imagens, treina um
backbone mobile e exporta um `.tflite` com a mesma interface usada por
`AppContainer`:

- entrada `float32 [1,224,224,3]` em `0..255` (a normalização ImageNet está
  dentro do grafo exportado);
- saída softmax `[1,N]`;
- `labels.txt` e `labels_ptbr.json` na mesma ordem da saída.

## Resultado da primeira rodada (`v1-b0`)

- **22.295 imagens** (14.786 de interior + 7.509 do iNaturalist), 59 classes.
- Teste completo (2.232 imagens): **top-1 90,2% / top-3 96,6%** (TFLite float16).
- Subconjunto iNaturalist (753 imagens): **81,5% / 92,7%**.
- Nas 435 fotos em que os dois modelos têm classes em comum: novo **79,8% /
  93,1%**; anterior **30,6% / 46,9%**.
- Modelo final: 8,2 MB float16 (o anterior tinha 29 MB).

## Estratégia de dados

1. **47 classes atuais** — dataset `kakasher/house-plant-species` (14.790
   imagens, CC BY 3.0). É a base principal, porque cobre plantas de vaso.
2. **12 espécies da base local que o modelo atual não cobre** — rosa, jasmim,
   hortênsia, gerânio, cravo, lavanda, alecrim, manjericão, hortelã, peperômia,
   clorofito e girassol. Fotos do iNaturalist (CC0/CC BY/CC BY-SA), priorizando
   o Brasil.
3. **Complemento de domínio real** — até 150 fotos de iNaturalist por classe
   para as 47 atuais, com o mesmo filtro de licença. Isso reduz o viés de fotos
   de estúdio/Bing e ajuda em fotos de celular.

O Pl@ntNet-300K não entra na primeira rodada: são 31,7 GB de flora
predominantemente europeia, e o custo de download/armazenamento não se paga
para plantas de vaso brasileiras. Ele pode ser usado depois como pré-treino
auxiliar, se houver ganho medido.

### Licenças

- `house-plant-species`: CC BY 3.0 (atribuição).
- iNaturalist: apenas observações `research` com foto CC0/CC BY/CC BY-SA. O
  `data/inat/manifest.csv` guarda autor, licença e URL de cada foto. As imagens
  **não** vão para o APK; o manifesto serve para atribuição e auditoria.
- O modelo treinado é distribuído junto com o app; revise a licença das fotos
  antes de qualquer uso comercial.

## Estrutura

```text
training/
├── classes.json              # 59 classes (47 atuais + 12 da base local)
├── data/
│   ├── houseplant_raw/       # extraído do tar (47 pastas)
│   ├── inat/                 # fotos do iNaturalist + manifest.csv
│   └── manifest.csv          # path,class_key,group,source
├── scripts/
│   ├── build_classes.py
│   ├── query_inat.py
│   ├── download_inat.py
│   ├── prepare_manifest.py
│   ├── train.py
│   ├── export_tflite.py
│   └── eval_tflite.py
└── artifacts/<run>/          # best.pt, métricas, TFLite exportado
```

## Setup

```bash
cd training
uv venv --python 3.12 .venv
. .venv/bin/activate
uv pip install --index-url https://download.pytorch.org/whl/cu128 torch torchvision
uv pip install timm pillow pandas numpy tqdm requests scikit-learn onnx onnxruntime
uv pip install onnx2tf tf-keras
```

## Pipeline

```bash
# 1. Lista de classes (já gerada)
python scripts/build_classes.py

# 2. Resolve táxons e conta fotos disponíveis
python scripts/query_inat.py

# 3. Baixa o dataset das 47 classes (uma vez)
curl -L -C - -o data/house_plant_species.tar \
  https://huggingface.co/datasets/kakasher/house-plant-species/resolve/main/house_plant_species.tar
mkdir -p data/houseplant_raw
tar -xf data/house_plant_species.tar -C data/houseplant_raw --strip-components=1

# 4. Baixa o complemento do iNaturalist (retomável)
python -u scripts/download_inat.py

# 5. Junta tudo no manifesto
python scripts/prepare_manifest.py

# 6. Treino (RTX 3070 8 GB)
python -u scripts/train.py \
  --arch mobilenetv3_large_100.ra_in1k \
  --epochs 20 --freeze-epochs 2 --batch-size 64 --workers 8 \
  --run-name v1-b0

# 7. Exporta ONNX -> TFLite (float16 por padrão) e valida contra o PyTorch
python -u scripts/export_tflite.py --run v1-b0

# 8. Avalia o TFLite no split de teste
python -u scripts/eval_tflite.py \
  --tflite artifacts/v1-b0/export/houseplant_model.tflite \
  --labels artifacts/v1-b0/export/houseplant_labels.txt \
  --manifest artifacts/v1-b0/test.csv
```

Para um teste rápido do pipeline, use `--limit-per-class 15 --epochs 1
--freeze-epochs 0 --batch-size 32 --workers 4 --run-name smoke`.

## O que olhar no resultado

- `artifacts/<run>/metrics.json`: top-1/top-3 de validação e teste.
- `classification_report.txt` e `confusion.csv`: classes confundidas.
- `export/houseplant_model.tflite`: tamanho final (8,2 MB em float16; 4,6 MB
  em dynamic-range int8), contra 29 MB do modelo anterior.
- Comparação justa: avalie o modelo atual e o novo no **mesmo** conjunto de
  fotos do iNaturalist (domínio que o modelo atual nunca viu). O split de
  teste do dataset de interior é útil, mas o modelo atual já viu essas imagens.

## Integração no app

Quando o número no conjunto real justificar a troca:

```bash
cp artifacts/v1-b0/export/houseplant_model.tflite \
   ../app/src/main/assets/models/houseplant_model.tflite
cp artifacts/v1-b0/export/houseplant_labels.txt \
   ../app/src/main/assets/models/houseplant_labels.txt
cp artifacts/v1-b0/export/houseplant_labels_ptbr.json \
   ../app/src/main/assets/models/houseplant_labels_ptbr.json
```

Depois rode `./gradlew testDebugUnitTest assembleDebug` e valide fotos reais.
O `PlantClassifier` já usa `normalizeToUnit = false`, então não é preciso mexer
no pré-processamento. Se o modelo exportado mudar para entrada `0..1`, ajuste
`AppContainer` para `normalizeToUnit = true`.

## Limitações

- Fotos do iNaturalist são de plantas na natureza, não de vasos; o dataset de
  interior cobre o vaso, o iNaturalist cobre fundo/luz/ângulo reais.
- As 12 classes novas têm menos imagens que as 47 antigas; o sampler compensa
  parcialmente, mas classes com <100 fotos podem ficar fracas.
- O split é por observação do iNaturalist, mas o dataset de interior não tem
  metadados de duplicata; pode haver vazamento de imagens quase idênticas.
- A avaliação final precisa de fotos suas, do seu celular, com o vaso e a luz
  reais. Sem isso, o número de validação é só um proxy.
