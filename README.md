# Chroma Subsampling Image Compression Pipeline (Scala & Chisel)

A hardware‑accelerated image compression pipeline implemented in **Scala** and **Chisel3** that converts RGB frames to YCbCr, applies adaptive chroma subsampling on Cb/Cr channels, and reconstructs the data back to RGB. Designed for FPGA/ASIC integration with local‑only processing and parameterizable subsampling ratios.

---

## Features

- **Scala & Chisel3**: Full compression pipeline expressed as parametrizable hardware modules.
- **RGB → YCbCr Converter**: Fixed‑point modules computing Y, Cb, and Cr channels per ITU‑R BT.601.
- **Chroma Subsampling Module**: Supports common modes (4:4:4, 4:2:2, 4:2:0) and custom H/V ratios.
- **Upsampling & Reconstruction**: Interpolation‑based upsampler and RGB reconstructor blocks.
- **Tile‑Based Local Processing**: Streamed per‑tile or per‑line operation, no full‑frame buffers required.
- **Verilog Generation**: SBT tasks for generating synthesizable Verilog and FIRRTL.
- **Testbenches & Assertions**: ChiselTest‑driven unit tests and formal property checks.

---