package jpeg

import chisel3._
import chisel3.util._

class ImageCompressorTop(width: Int, height: Int, subMode: Int, downFactor: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new PixelBundle))
    val out = Decoupled(new PixelYCbCrBundle)
    val sof = Input(Bool())
    val eol = Input(Bool())
  })

  val fixedBitWidth = 8

  val toYC    = Module(new RGB2YCbCr)
  val chroma  = Module(new ChromaSubsampler(
    imageWidth = width,
    imageHeight = height,
    bitWidth = fixedBitWidth
  ))
  val spatial = Module(new SpatialDownsampler(width, height, downFactor))
  val quant   = Module(new ColorQuantizer)

  val selectedChromaMode = Wire(ChromaSubsamplingMode())
  
  switch (subMode.U) {
    is(0.U) { selectedChromaMode := ChromaSubsamplingMode.CHROMA_444 }
    is(1.U) { selectedChromaMode := ChromaSubsamplingMode.CHROMA_422 }
    is(2.U) { selectedChromaMode := ChromaSubsamplingMode.CHROMA_420 }
  }
  chroma.io.mode := selectedChromaMode

  toYC.io.in <> io.in
  toYC.io.out <> spatial.io.in
  
  spatial.io.sof := io.sof
  spatial.io.eol := io.eol

  spatial.io.out <> quant.io.in

  chroma.io.validIn := quant.io.out.valid
  chroma.io.dataIn  := quant.io.out.bits.asTypeOf(new YCbCrPixel(fixedBitWidth))
  quant.io.out.ready := io.out.ready

  io.out.valid := chroma.io.validOut
  io.out.bits  := chroma.io.dataOut.asTypeOf(new PixelYCbCrBundle)
}
