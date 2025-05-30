package Chroma_Subsampling_Image_Compressor

import chisel3._
import chisel3.util._

/** 
 * Converts an 8‑bit RGB pixel into 8‑bit YCbCr using fixed‑point arithmetic.
 */
class RGB2YCbCr extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new PixelBundle))
    val out =      Decoupled(new PixelYCbCrBundle)
  })

  // Zero‑extend 8‑bit channels into 9‑bit signed so 0–255 stays positive
  val rS = Cat(0.U(1.W), io.in.bits.r).asSInt
  val gS = Cat(0.U(1.W), io.in.bits.g).asSInt
  val bS = Cat(0.U(1.W), io.in.bits.b).asSInt

  // Fixed‑point multiply
  val yInt  =  77.S  * rS + 150.S  * gS +  29.S  * bS
  val cbInt = -43.S  * rS -  85.S  * gS + 128.S  * bS
  val crInt = 128.S  * rS - 107.S  * gS -  21.S  * bS

  // Helper: clamp an SInt into the 0..255 range (returns UInt)
  private def clampUnsigned(x: SInt): UInt = {
    Mux(x < 0.S,        0.U,
    Mux(x > 255.S, 255.U, x.asUInt))
  }

  // Shift‑right 8 with a 128 bias on the intermediate, then clamp
  val y  = clampUnsigned((yInt  + 128.S) >> 8)
  val cb = clampUnsigned(((cbInt + 128.S) >> 8) + 128.S)
  val cr = clampUnsigned(((crInt + 128.S) >> 8) + 128.S)

  // Drive outputs and back‑pressure
  io.out.bits.y    := y
  io.out.bits.cb   := cb
  io.out.bits.cr   := cr
  io.out.valid     := io.in.valid
  io.in.ready      := io.out.ready
}
