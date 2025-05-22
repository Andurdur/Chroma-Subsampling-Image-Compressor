package jpeg

import chisel3._

object PixelBundles {
  class PixelRGB extends Bundle {
    val r = UInt(8.W)
    val g = UInt(8.W)
    val b = UInt(8.W)
  }

  class PixelYCbCr extends Bundle {
    val y  = UInt(8.W)
    val cb = UInt(8.W)
    val cr = UInt(8.W)
  }
}
