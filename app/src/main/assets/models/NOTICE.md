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

`houseplant_model.tflite` — EfficientNetB4, 47 houseplant species, trained on
the House Plant Species dataset. Input [0,255]. Dataset/model are used under the
source terms for this personal non-commercial project; see root
THIRD_PARTY_NOTICES.md.
