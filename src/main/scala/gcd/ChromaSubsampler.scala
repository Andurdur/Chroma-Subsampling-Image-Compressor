package jpeg

import chisel3._
import chisel3.util._

/** No‑op chroma subsampler (stub) */
class ChromaSubsampler extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new PixelYCbCrBundle))
    val out =     Decoupled(new PixelYCbCrBundle)
  })
  // pass through
  io.out <> io.in
}
