# EuPlantei species classifier — model card

## Modelo

- Arquitetura: EfficientNet-B0 (`timm` `efficientnet_b0.ra_in1k`), fine-tuned.
- Entrada: `float32 [1,224,224,3]`, faixa `0..255` (normalização ImageNet embutida no grafo).
- Saída: softmax `[1,59]`.
- Tamanho: TFLite float16, 8,2 MB (dynamic-range int8 alternativo: 4,6 MB, top-1 89,4%).
- Classes: 59 rótulos de exibição que cobrem 52 `speciesId` da base local
  (47 classes do modelo de interior + 12 ornamentais/hortaliças brasileiras).

## Dados de treino

- `kakasher/house-plant-species`: 14.790 imagens no dataset, 14.786 usadas após
  validação, 47 classes, CC BY 3.0.
- iNaturalist: 7.959 fotos baixadas (CC0/CC BY/CC BY-SA), 7.509 usadas após
  remover as classes genéricas que misturavam espécies irmãs.
- Split: 80/10/10 por classe; no iNaturalist o grupo é a observação, para evitar
  vazamento de fotos da mesma planta.
- Augmentation: RandomResizedCrop, flip, rotação, perspectiva, cor, blur e
  RandomErasing.
- Treino: 20 épocas, 2 congeladas, AdamW, label smoothing 0.1, AMP.

## Métricas

- Dataset completo (2.232 imagens): **top-1 90,2% / top-3 96,6%** no TFLite float16.
- Subconjunto iNaturalist (753 imagens): **top-1 81,5% / top-3 92,7%**.
- Modelo anterior no mesmo subconjunto iNaturalist, nas 47 classes em comum
  (435 imagens): **top-1 30,6% / top-3 46,9%**; o novo modelo nessas mesmas 435
  imagens: **79,8% / 93,1%**.
- Concordância TFLite float16 vs PyTorch: **100%** em 120 imagens, max|diff| 0,005.
- Melhor época: 15; validação top-1 91,3%.

## Uso pretendido

- Sugestão de espécie no app EuPlantei, offline, sempre com top-3 e confirmação
  do usuário para plantas de vaso.
- Não é autoridade botânica; não usar para decisões de consumo, toxicidade ou
  tratamento de doenças sem confirmação.

## Limitações

- Cobre 59 classes; plantas fora do catálogo caem na classe mais parecida. Ainda
  não há classe "não é planta" nem rejeição de out-of-distribution.
- Fotos do iNaturalist são de plantas na natureza; o dataset de interior tem
  vasos, mas também watermarks e possíveis duplicatas.
- Classes com menos imagens (ex.: manjericão, clorofito) tendem a ser mais fracas.
- O modelo é derivado de imagens CC BY 3.0 e CC BY/CC BY-SA; uso pessoal e não
  comercial nesta etapa. Revise as licenças antes de distribuir comercialmente.

## Arquivos

- `artifacts/<run>/best.pt`
- `artifacts/<run>/export/houseplant_model.tflite`
- `artifacts/<run>/export/houseplant_labels.txt`
- `artifacts/<run>/export/houseplant_labels_ptbr.json`
