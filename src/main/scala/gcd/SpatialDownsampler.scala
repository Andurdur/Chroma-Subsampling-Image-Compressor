package jpeg

import chisel3._
import chisel3.util._

/** No‑op spatial downsampler (stub) */
class SpatialDownsampler extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new PixelBundle))
    val out =     Decoupled(new PixelBundle)
  })
  // just pass through
  io.out <> io.in
}
