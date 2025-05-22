package jpeg

import chisel3._

class ImageCompressorTop extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new PixelBundle))
    val out =     Decoupled(new PixelYCbCrBundle)
  })

  // instantiate pipeline
  val toYC   = Module(new RGB2YCbCr)
  val spatial= Module(new SpatialDownsampler)
  val quant  = Module(new ColorQuantizer)
  val chroma = Module(new ChromaSubsampler)

  // wire it up
  toYC.io.in   <> io.in
  toYC.io.out  <> spatial.io.in
  spatial.io.out <> quant.io.in
  quant.io.out   <> chroma.io.in
  chroma.io.out  <> io.out
}
