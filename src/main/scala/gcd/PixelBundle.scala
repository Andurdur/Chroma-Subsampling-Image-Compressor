package jpeg

import chisel3._

/** Simple 8‑bit RGB pixel */
class PixelBundle extends Bundle {
  val r = UInt(8.W)
  val g = UInt(8.W)
  val b = UInt(8.W)
}

/** Simple 8‑bit YCbCr pixel */
class PixelYCbCrBundle extends Bundle {
  val y  = UInt(8.W)
  val cb = UInt(8.W)
  val cr = UInt(8.W)
}
