package Chroma_Subsampling_Image_Compressor

import chisel3._
import chisel3.util._

/**
 * Utility functions for converting between YCbCr and RGB colorspaces.
 */
object YCbCrUtils {
  /**
   * Inverts JPEG-style YCbCr back to RGB.
   * @param y  Luma component (0–255)
   * @param cb Chroma-blue offset (0–255)
   * @param cr Chroma-red offset (0–255)
   * @return    Tuple of (R, G, B), each clamped to 0–255
   */
  def ycbcr2rgb(y: Int, cb: Int, cr: Int): (Int, Int, Int) = {
    val c = y
    val d = cb - 128
    val e = cr - 128
    def clamp(v: Int): Int = math.max(0, math.min(255, v))
    val r = clamp((298 * c + 409 * e + 128) >> 8)
    val g = clamp((298 * c - 100 * d - 208 * e + 128) >> 8)
    val b = clamp((298 * c + 516 * d + 128) >> 8)
    (r, g, b)
  }
}
