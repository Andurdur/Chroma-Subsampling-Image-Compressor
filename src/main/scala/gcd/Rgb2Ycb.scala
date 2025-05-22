package jpeg

import chisel3._
import chisel3.util._
import PixelBundles._


class RGB2YCbCr extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new PixelRGB))
    val out = Decoupled(new PixelYCbCr)
  })

  io.in.ready  := io.out.ready
  io.out.valid := io.in.valid

  val r = io.in.bits.r.asSInt
  val g = io.in.bits.g.asSInt
  val b = io.in.bits.b.asSInt


  val yInter  = (  77.S * r) + (150.S * g) + (29.S  * b)
  val cbInter = ((-43.S * r) + (-85.S * g) + (128.S * b))
  val crInter = ((128.S * r) + (-107.S * g) + (-21.S  * b))

  val yRaw  = ((yInter  + 128.S) >> 8).asUInt
  val cbRaw = (((cbInter + 128.S) >> 8).asUInt) + 128.U
  val crRaw = (((crInter + 128.S) >> 8).asUInt) + 128.U

  def clip8(x: UInt): UInt = Mux(x > 255.U, 255.U, x)
  val y  = clip8(yRaw)
  val cb = clip8(cbRaw)
  val cr = clip8(crRaw)

  io.out.bits.y  := y
  io.out.bits.cb := cb
  io.out.bits.cr := cr
}
