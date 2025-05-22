package jpeg

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

  // Zero‑extend the 8‑bit channels into 9‑bit signed so [0..255] stays positive
  val rS = Cat(0.U(1.W), io.in.bits.r).asSInt
  val gS = Cat(0.U(1.W), io.in.bits.g).asSInt
  val bS = Cat(0.U(1.W), io.in.bits.b).asSInt

  // Fixed‑point multiply
  val yInt  =  77.S  * rS + 150.S  * gS +  29.S  * bS
  val cbInt = -43.S  * rS -  85.S  * gS + 128.S  * bS
  val crInt = 128.S  * rS - 107.S  * gS -  21.S  * bS

  // Shift‑right 8 with bias and clamp to [0,255]
  private def clamp(x: SInt): UInt = {
    val shifted = (x + 128.S) >> 8
    Mux(shifted < 0.S, 0.U, Mux(shifted > 255.S, 255.U, shifted.asUInt))
  }

  val y  = clamp(yInt)
  val cb = clamp(cbInt) + 128.U
  val cr = clamp(crInt) + 128.U

  // Drive outputs and back‑pressure
  io.out.bits.y  := y
  io.out.bits.cb := cb
  io.out.bits.cr := cr
  io.out.valid   := io.in.valid
  io.in.ready    := io.out.ready
}
