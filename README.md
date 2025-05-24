# Chroma Subsampling Image Compression Pipeline (Scala & Chisel)

A hardware‑accelerated image compression pipeline implemented in **Scala** and **Chisel3** that converts RGB frames to YCbCr, applies adaptive chroma subsampling on Cb/Cr channels, and reconstructs the data back to RGB. Designed for FPGA/ASIC integration with local‑only processing and parameterizable subsampling ratios.

---

## Modules

- **RGB2YCB**  
  Converts incoming RGB pixel streams into Y (luma), Cb, and Cr (chroma) channels.

- **ChromaSubsampler**  
  Locally downsamples Cb/Cr channels (e.g. 4:4:4 → 4:2:2 or 4:2:0) on a per‑block or per‑row basis.

- **SpatialDownSampler**  
  Further luma and chroma downsampling via configurable kernel/window sizes.

- **ColorQuantizer**  
  Applies bit–width reduction or palette quantization to Y, Cb, and Cr channels.

- **PixelBundle**  
  Recombines processed luma/chroma into packetized pixel bundles for downstream DMA or memory write‑back.

---

| Component          | Status      | Notes                                        |
| ------------------ | ----------- | -------------------------------------------- |
| RGB2YCbCr          | Complete    | Tests pass against `ReferenceModel`          |
| SpatialDownsampler | Complete    | Extensively tested with ScalaTest/ChiselTest |
| ChromaSubsampler   | In progress | Currently a no-op pass-through stub          |
| ColorQuantizer     | In progress | Currently a no-op pass-through stub          |
| ImageCompressorTop | In progress | Top-level wires modules together             |

## Building & Running Tests
From the project root:

1. **Compile** the code:  
   ```bash
   sbt compile

1. **Run all Tests**: 
    ```bash
    sbt test

## Requirements

- **Scala 2.13+**  
- **sbt 1.5+**  
- **Chisel 3.5.x** (or later)  
- **ChiselTest** (for simulation)  
- **FIRRTL Compiler** (automatically pulled in via sbt)

---