package jpeg

/** Pure‐Scala “golden” model for rgb→ycbcr conversion */
object ReferenceModel {
  case class PixelRGB(r: Int, g: Int, b: Int)
  case class PixelYCbCr(y: Int, cb: Int, cr: Int)

  def rgb2ycbcr(p: PixelRGB): PixelYCbCr = {
    // fixed‐point coefficients
    val yInter  =  77*p.r + 150*p.g + 29*p.b
    val cbInter = -43*p.r - 85*p.g + 128*p.b
    val crInter = 128*p.r -107*p.g - 21*p.b

    def clamp(v: Int): Int = math.max(0, math.min(255, v))
    val y  = clamp((yInter  + 128) >> 8)
    val cb = clamp(((cbInter + 128) >> 8) + 128)
    val cr = clamp(((crInter + 128) >> 8) + 128)
    PixelYCbCr(y, cb, cr)
  }
}
