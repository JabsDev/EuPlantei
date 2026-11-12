# Third-party notices

## llama.cpp / ggml

Source: https://github.com/ggml-org/llama.cpp
License: MIT
Vendored copy: `app/src/main/cpp/llama.cpp` (upstream commit e3de71522)
The upstream `LICENSE` file is kept inside the vendored directory.

## TensorFlow Lite

Source: https://www.tensorflow.org/lite
License: Apache License 2.0
Used for on-device inference of the plant disease model.

## MobileNetV2 / PlantVillage model

The demo model in `app/src/main/assets/models/plant_model.tflite` is a MobileNetV2
with dynamic-range int8 quantization, trained on the PlantVillage dataset (38 classes,
14 crops). It was obtained from the public CropScan project:
https://github.com/priyanshu-1git/crop_scan

The PlantVillage dataset has its own usage terms. This model is included for
educational/demonstration purposes. **Verify the dataset and source repository
licenses before commercial distribution; replace the model with one that has a
compatible license if necessary.**

## Qwen 2.5 GGUF

Source: https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF
License: Apache 2.0
The GGUF is **not** bundled in the APK. It is downloaded or imported by the user.

## Open-Meteo

Source: https://open-meteo.com/
Free weather data without an API key. Usage subject to Open-Meteo terms.

## AndroidX, Jetpack Compose, Room, CameraX, DataStore, Navigation, Coil, OkHttp

Licenses: Apache License 2.0 / BSD, according to each project.

## Material Icons

License: Apache License 2.0.

## Test-only photo

The photo used during manual emulator testing (tomato late blight) came from
Wikimedia Commons and is **not** packaged in the APK.

## Embrapa PDDB / Digipathos

Source: https://www.digipathos-rep.cnptia.embrapa.br/ (DOI 10.48432/XA1OVL)
License declared by the official Embrapa/Redape portal: CC BY-NC 4.0
Not included in this build. It is not Apache 2.0 according to the official
portal; commercial use would require permission or another dataset with a
compatible license.


## Embrapa Corpus (br-llm-data/embrapa-corpus-cs)

Source: https://huggingface.co/datasets/br-llm-data/embrapa-corpus-cs
Type: gated textual corpus (Parquet) of Embrapa publications
License: `other`; each row also exposes `license`/`rights` fields from the source.
Not included in this build. It is not an image dataset and cannot train the
TFLite plant classifier. It could be used for local RAG or LLM fine-tuning,
subject to the source-document rights and Hugging Face access approval.

## Houseplant Species TFLite model

Dataset: https://huggingface.co/datasets/kakasher/house-plant-species
Model: https://huggingface.co/AlyModrik41/House-Plants-Classification-TFLite-Model
Dataset: 14,790 images, 47 houseplant classes. The dataset card declares
CC BY 3.0 but also asks for personal use due to image copyright considerations.
The model repository has no explicit license. Included for this personal,
non-commercial project; review the source rights before redistribution or
commercial use.
Input: float32 [1,224,224,3], [0,255]; output: [1,47] softmax.
