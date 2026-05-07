Place trained TensorFlow Lite models here as:

    waste_model.tflite
    cleanup_model.tflite

The waste classifier expects:
- input tensor: 1 x 224 x 224 x 3, FLOAT32, UINT8, or INT8
- output tensor: 1 x 7
- label order matching labels.txt

The cleanup verifier accepts either:
- image-level output: one waste-probability value
- clean/waste output: two values where index 1 is waste
- segmentation-style output with the last dimension of 2, where channel 1 is waste

Do not commit dummy model files. Invalid .tflite assets will make runtime
initialization fail; the app catches that and falls back to Gemini and ML Kit
without blocking reporting or cleanup.
