package Chroma_Subsampling_Image_Compressor

import chisel3._

class PixelBundle extends Bundle {
  val r = UInt(8.W)
  val g = UInt(8.W)
  val b = UInt(8.W)
}

class PixelYCbCrBundle extends Bundle {
  val y  = UInt(8.W)
  val cb = UInt(8.W)
  val cr = UInt(8.W)
}
