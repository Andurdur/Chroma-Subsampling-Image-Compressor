package jpeg

import chisel3._
import chisel3.util._

class ImageCompressorTop(width: Int, height: Int, subMode: Int, downFactor: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new PixelBundle))
    val out =     Decoupled(new PixelYCbCrBundle)
    val sof = Input(Bool())
    val eol = Input(Bool())
  })

  val toYC    = Module(new RGB2YCbCr)
  val chroma  = Module(new ChromaSubsampler)
  val spatial = Module(new SpatialDownsampler(width, height, downFactor))
  val quant   = Module(new ColorQuantizer)

  // wire up frame signals
  toYC.io.in   <> io.in
  toYC.io.out  <> spatial.io.in
  spatial.io.sof := io.sof
  spatial.io.eol := io.eol

  spatial.io.out <> quant.io.in
  quant.io.out   <> chroma.io.in
  chroma.io.out  <> io.out
}

