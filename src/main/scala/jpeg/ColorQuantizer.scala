package Chroma_Subsampling_Image_Compressor

import chisel3._
import chisel3.util._

/** No‑op quantizer (stub) */
class ColorQuantizer extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new PixelYCbCrBundle))
    val out =     Decoupled(new PixelYCbCrBundle)
  })
  // just pass through
  io.out <> io.in
}
