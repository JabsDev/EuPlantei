# Model notice

`plant_model.tflite` — MobileNetV2 (dynamic-range int8 weights) trained on
PlantVillage, 38 classes across 14 crops. Source: public CropScan repository
(https://github.com/priyanshu-1git/crop_scan). Included for educational use.

Input: float32 `[1,224,224,3]`, normalized to `[0,1]`.
Output: float32 `[1,38]` softmax.
Labels: `labels.txt`, same order as the model outputs.
PT-BR names: `labels_ptbr.json`.

PlantVillage has its own dataset terms. Verify licensing before commercial
distribution and replace the model if needed. See the project root
`THIRD_PARTY_NOTICES.md`.

The Embrapa PDDB/Digipathos dataset is not used in this build. Its official
license is CC BY-NC 4.0 (not Apache 2.0). See the project root notices.

`houseplant_model.tflite` — EuPlantei Species, EfficientNet-B0 fine-tuned,
59 output labels covering 52 `speciesId`. The classes are the 47 houseplant
labels of the previous model plus 12 Brazilian ornamentals/herbs (rosa, jasmim,
hortênsia, gerânio, cravo, lavanda, alecrim, manjericão, hortelã, peperômia,
clorofito, girassol). Input float32 `[1,224,224,3]` in `[0,255]` with ImageNet
normalization baked into the graph; output `[1,59]` softmax. TFLite float16,
about 8.2 MB. SHA-256: `0f295c10ebf27c320026e0091ce514944b75809d626b56d7ee43ab944eed51c4`.
Held-out test: top-1 90.2% / top-3 96.6%; iNaturalist-only subset:
81.5% / 92.7%. Training data: House Plant Species (CC BY 3.0) and iNaturalist
photos under CC0/CC BY/CC BY-SA. The reproducible pipeline and model card are in
`training/`. Review the dataset terms before commercial distribution; this build
is personal/non-commercial.
