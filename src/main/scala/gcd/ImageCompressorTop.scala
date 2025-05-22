package jpeg

import chisel3._
import chisel3.util._
import PixelBundles._

class ImageCompressorTop(width: Int, height: Int, subMode: Int, downFactor: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new PixelRGB))
    val out = Decoupled(new PixelYCbCr)
  })

  val conv = Module(new RGB2YCbCr)
  val subs = Module(new ChromaSubsampler(width, height, subMode))
  val spat = Module(new SpatialDownsampler(width, height, downFactor))
  val quant = Module(new ColorQuantizer)

  conv.io.in <> io.in
  conv.io.out <> subs.io.in

  subs.io.sof := false.B
  subs.io.eol := false.B
  subs.io.out <> spat.io.in

  spat.io.sof := false.B
  spat.io.eol := false.B
  spat.io.out <> quant.io.in

  quant.io.out <> io.out
}
