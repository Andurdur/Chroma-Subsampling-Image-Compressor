# Chroma Subsampling Image Compression Pipeline (Scala & Chisel)

A hardware‑accelerated image compression pipeline implemented in **Scala** and **Chisel3** that converts RGB frames to YCbCr, applies adaptive chroma subsampling on Cb/Cr channels, and reconstructs the data back to RGB. Designed for FPGA/ASIC integration with local‑only processing and parameterizable subsampling ratios.

---

## Modules

- **RGB2YCB**  
  Converts incoming RGB pixel streams into Y (luma), Cb, and Cr (chroma) channels per ITU‑R BT.601.

- **ChromaSubsampler**  
  Locally downsamples Cb/Cr channels (e.g. 4:4:4 → 4:2:2 or 4:2:0) on a per‑block or per‑row basis.

- **SpatialDownSampler**  
  Optional further luma and chroma downsampling via configurable kernel/window sizes.

- **ColorQuantizer**  
  Applies bit–width reduction or palette quantization to Y, Cb, and Cr channels.

- **PixelBundle**  
  Recombines processed luma/chroma into packetized pixel bundles for downstream DMA or memory write‑back.

---

