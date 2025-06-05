**Chroma Subsampling Image Compression Pipeline (Scala & Chisel)**

---

## Project Overview

This repository implements a hardware-accelerated image compression pipeline in **Scala** and **Chisel3**, targeting FPGA/ASIC integration. The pipeline’s primary goal is to take a stream of RGB pixels, convert them to YCbCr, apply configurable chroma subsampling (e.g., 4:4:4 → 4:2:2 or 4:2:0), optionally perform further spatial downsampling and color quantization, then reconstruct the data back into packetized RGB bundles. All processing is done locally (no off-chip or host-side support), and subsampling/quantization parameters are parameterizable at runtime or generate-time.

---

## Table of Contents

1. [Features](#features)  
2. [How to Use (Generator Interface)](#how-to-use-generator-interface)  
3. [Testing](#testing)  
4. [Progress Tree](#progress-tree)  
5. [Requirements](#requirements)  

---

## Features

1. **RGB -> YCbCr Conversion**  
   - **Module:** `RGB2YCbCr`  
   - **Description:**  
     - Converts each incoming 24-bit RGB pixel into three separate channels: Y (luma), Cb, and Cr (chroma).  
     - Uses the standard fixed-point equations:  
       ```
       Y  = clamp(  77·R + 150·G +  29·B + 128 ) >> 8  
       Cb = clamp( -43·R -  85·G + 128·B + 128 ) >> 8  
       Cr = clamp( 128·R - 107·G -  21·B + 128 ) >> 8  
       ```
     - All multipliers and shifts are implemented in pure integer arithmetic; results are clamped to the 0–255 range.  

2. **Adaptive Chroma Subsampling**  
   - **Module:** `ChromaSubsampler`  
   - **Description:**  
     - Supports parameterizable subsampling ratios (e.g., 4:4:4 → 4:2:2, 4:2:0, etc.).  
     - Locally downsamples the Cb/Cr channels on a per-block or per-row basis (configurable at generate-time).  

3. **Spatial Downsampling**  
   - **Module:** `SpatialDownSampler`  
   - **Description:**  
     - Further reduces resolution of Y (and optionally Cb/Cr) channels via a configurable 2D kernel (e.g., average pooling).  
     - Kernel size (e.g., 2×2, 4×4) and stride can be adjusted through parameters.  
     - Fully implemented and validated through ScalaTest/ChiselTest.  

4. **Color Quantization**  
   - **Module:** `ColorQuantizer`  
   - **Description:**  
     - Applies bit-width reduction (e.g., 8 bits → 4 bits per component) or palette indexing to Y, Cb, and Cr.  
     - Intended to reduce memory footprint and downstream bandwidth.  

5. **Pixel Bundling (& Packetization)**  
   - **Module:** `PixelBundle`  
   - **Description:**  
     - Reassembles processed Y/Cb/Cr (or re-converted RGB) into a packetized bundle suitable for DMA or on-chip memory write-back.  
     - Packs multiple pixels (e.g., 4 or 8 at a time) into a wider bus (e.g., 64 bits or 128 bits) to maximize throughput.  

6. **YCbCr → RGB Reconstruction**  
   - **Module:** `YCbCrUtils` (in `YCbCr2RGB.scala`)  
   - **Description:**  
     - Inverts JPEG-style YCbCr back to RGB after processing (e.g., for visualization or verification).  
     - Uses the standard inverse formulas:  
       ```
       R = clamp((298·Y + 409·(Cr−128) + 128) >> 8)  
       G = clamp((298·Y − 100·(Cb−128) − 208·(Cr−128) + 128) >> 8)  
       B = clamp((298·Y + 516·(Cb−128) + 128) >> 8)  
       ```  
     - Ensures each channel (R, G, B) is clamped to the 0–255 range.  

7. **Top-Level Integration**  
   - **Module:** `ImageCompressorTop`  
   - **Description:**  
     - Orchestrates the full processing pipeline end to end:  
       1. **Read** input RGB pixel stream (from memory or test bench).  
       2. **Convert** RGB → YCbCr using `RGB2YCbCr`.  
       3. **Optionally subsample** Cb/Cr via `ChromaSubsampler`.  
       4. **Optionally spatially downsample** Y/Cb/Cr via `SpatialDownSampler`.  
       5. **Optionally quantize** Y/Cb/Cr via `ColorQuantizer`.  
       6. **Re-packetize** processed channels via `PixelBundle`.  
       7. **Reconstruct** YCbCr → RGB (for visualization/verification) using `YCbCrUtils` (in `YCbCr2RGB.scala`).  
     - Produces a compressed, processed pixel stream and, if desired, a reconstructed RGB image for comparison.  
     - All parameters (subsampling ratio, spatial kernel size, quantization levels, and pipeline order) are generate-time constants passed via the Scala/Chisel generator.  

8. **Software-Reference Image Processor Model**  
   - **Module:** `ImageProcessorModel` (in `ImageProcessorModel.scala`)  
   - **Description:**  
     - Provides utilities to read/write PNG or JPEG images using the Scrimage library.  
     - `getImageParams(...)` creates an `ImageProcessorParams` structure (width, height, numPixelsPerCycle, default chroma parameters).  
     - `getImagePixels(...)` extracts a 2D Scala `Seq[Seq[Seq[Int]]]` (height×width×RGB) to serve as a software reference.  
     - `writeImage(...)` functions allow writing intermediate stage outputs (YCbCr, subsampled, quantized pixel arrays) back to disk as PNG.  

---

## How to Use (Generator Interface)

All generator entry points live in `src/main/scala/top/ImageCompressorTopApp.scala`. This object instantiates `ImageCompressorTop` with your chosen parameters. By default, it will emit a Chisel FIRRTL file and optionally run a small simulation that reads one of the `test_images/` PNG files and prints out the compressed, subsampled pixel stream in the console.

To run the generator:

```bash
# From repository root
$ sbt "Test / runMain jpeg.ImageCompressionApp"
```
Available command-line parameters (all optional, with defaults):

| Parameter                      | Description                                                                                                                                                                                    | Default / Example             |
| ------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------- |
| `--input=<path>`               | **Input image file** (`inputPath`), e.g.: `test_images/in128x128.png`. <br>Internally, `val inputPath = "<path>"` and `val imageName = new File(inputPath).getName.takeWhile(_!='.')`.         | `"test_images/in128x128.png"` |
| `--chromaA=<1 \| 2 \| 4>`      | **Chroma param\_a (horizontal sampling)** (in “J\:a\:b”). <br>`4` → 4:4\:x (no horizontal Cb/Cr subsampling); `2` → 4:2\:x; `1` → 4:1\:x.                                                      | `4`                           |
| `--chromaB=<0 \| param_a>`     | **Chroma param\_b (vertical sampling)** (in “J\:a\:b”). <br>If `param_b == param_a`: no vertical subsampling (e.g., 4:4:4, 4:2:2, 4:1:1). <br>If `param_b == 0`: subsample by 2 (e.g., 4:2:0). | `4`                           |
| `--quantY=<1–8>`               | **Quantization bits for Y channel** (luma). Valid range: 1–8.                                                                                                                                  | `8`                           |
| `--quantCb=<1–8>`              | **Quantization bits for Cb channel** (chroma-blue). Valid range: 1–8.                                                                                                                          | `8`                           |
| `--quantCr=<1–8>`              | **Quantization bits for Cr channel** (chroma-red). Valid range: 1–8.                                                                                                                           | `8`                           |
| `--spatial=<1 \| 2 \| 4 \| 8>` | **Spatial downsampling factor**. <br>`1` → no downsampling; `2` → 2×2 pooling; `4` → 4×4 pooling; `8` → 8×8 pooling.                                                                           | `1`                           |
| `--op1=<ProcessingStep.Type>`  | **Pipeline step 1**. Valid values: `SpatialSampling`, `ColorQuantization`, `ChromaSubsampling`.                                                                                                | `SpatialSampling`             |
| `--op2=<ProcessingStep.Type>`  | **Pipeline step 2**. Valid values: `SpatialSampling`, `ColorQuantization`, `ChromaSubsampling`.                                                                                                | `ColorQuantization`           |
| `--op3=<ProcessingStep.Type>`  | **Pipeline step 3**. Valid values: `SpatialSampling`, `ColorQuantization`, `ChromaSubsampling`.                                                                                                | `ChromaSubsampling`           |

**Note:**
Internally, these map to:
val selectedChromaParamA: Int      = <chromaA>
val selectedChromaParamB: Int      = <chromaB>
val yTargetQuantBits: Int          = <quantY>
val cbTargetQuantBits: Int         = <quantCb>
val crTargetQuantBits: Int         = <quantCr>
val selectedSpatialFactor: Int     = <spatial>
val op1_choice: ProcessingStep.Type = ProcessingStep.<op1>
val op2_choice: ProcessingStep.Type = ProcessingStep.<op2>
val op3_choice: ProcessingStep.Type = ProcessingStep.<op3>

---

## Testing

Run the Test

```bash
sbt test
```
Testing should run for Chroma Subsampling, Color Quantization, RGB2YCB, and Spatial Downsampling. Test images should produce for different chroma subsampling, color quantizer, spatial downsampling parameters. 

---

## Progress Tree

```text
Chroma-Subsampling-Image-Compressor/
├── modules/
│   ├── RGB2YCbCr             [✓] Complete 
│   ├── ChromaSubsampler      [✓] Complete  
│   ├── SpatialDownSampler    [✓] Complete  
│   ├── ColorQuantizer        [✓] Complete   
│   └── PixelBundle           [✓] Complete  
├── top/
│   ├── ImageCompressorTop    [✓] Complete 
│   │   └── Current: Wires submodules.  
│   └── ImageCompressorTopApp [✓] Complete  
│       └── CLI & generator for FIRRTL/Verilog + optional PNG dumps. 
└── test/
    ├── RGB2YCbCrTester           [✓] Passing  
    ├── SpatialDownSamplerTester   [✓] Passing  
    ├── ChromaSubsamplerTester     [✓] Passing   
    └── ColorQuantizerTester       [✓] Passing  
```

---

## Requirements

- **Scala 2.13+**  
- **sbt 1.5+**  
- **Chisel 3.5.x** (or later)  
- **ChiselTest** (for simulation)  
- **Scrimage 4.1.1** (for image and color functions)

---