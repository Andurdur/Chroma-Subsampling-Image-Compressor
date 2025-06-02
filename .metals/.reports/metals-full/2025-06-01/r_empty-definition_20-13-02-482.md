error id: file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/main/scala/jpeg/RGB2YCbCr.scala:`<none>`.
file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/main/scala/jpeg/RGB2YCbCr.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -chisel3/Int#
	 -chisel3/util/Int#
	 -Int#
	 -scala/Predef.Int#
offset: 604
uri: file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/main/scala/jpeg/RGB2YCbCr.scala
text:
```scala
package Chroma_Subsampling_Image_Compressor

import chisel3._
import chisel3.util._

object RGB2YCbCrUtils {
  /**
   * Fixed-point model for converting 8-bit RGB values into 8-bit YCbCr.
   *
   * The coefficients (77, 150, 29, etc.) approximate the standard
   * RGB→YCbCr transform when scaled by 2⁸, and the offsets/bias (128)
   * center the chroma channels.
   *
   * @param r_in  Red channel [0..255]
   * @param g_in  Green channel [0..255]
   * @param b_in  Blue channel [0..255]
   * @return      Tuple (Y, Cb, Cr), each clamped to [0..255]
   */
  def rgbToYCbCr(r_in: Int, g_in: Int, b_in: In@@t): (Int, Int, Int) = {
    val R = r_in
    val G = g_in
    val B = b_in

    // Multiply by fixed-point coefficients (scaled by 256)
    val yInt  =  77 * R + 150 * G +  29 * B
    val cbInt = -43 * R -  85 * G + 128 * B
    val crInt = 128 * R - 107 * G -  21 * B

    // Helper to clamp the final 8-bit result into [0..255]
    def clampUInt8(value: Int): Int = {
      if (value < 0) 0
      else if (value > 255) 255
      else value
    }

    // Add 128 bias before shifting right by 8 (divide by 256),
    // then apply chroma offsets (Cb/Cr center at 128)
    val y_final  = clampUInt8((yInt  + 128) / 256)
    val cb_final = clampUInt8(((cbInt + 128) / 256) + 128)
    val cr_final = clampUInt8(((crInt + 128) / 256) + 128)

    (y_final, cb_final, cr_final)
  }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.