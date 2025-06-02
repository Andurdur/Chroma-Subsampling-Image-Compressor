package Chroma_Subsampling_Image_Compressor

import chisel3._
import chisel3.util._

// Assuming PixelYCbCrBundle is defined in this package (e.g., in PixelBundle.scala)
// and has y, cb, cr fields, each UInt(8.W).

class ColorQuantizer(
    val yTargetBits: Int,
    val cbTargetBits: Int,
    val crTargetBits: Int,
    val originalBitWidth: Int = 8 // Defaulting to 8-bit original components
) extends Module {
  require(originalBitWidth > 0 && originalBitWidth <= 8, s"Original bit width must be between 1 and 8, inclusive. Got $originalBitWidth")
  require(yTargetBits >= 1 && yTargetBits <= originalBitWidth, s"Y target bits must be between 1 and $originalBitWidth. Got $yTargetBits")
  require(cbTargetBits >= 1 && cbTargetBits <= originalBitWidth, s"Cb target bits must be between 1 and $originalBitWidth. Got $cbTargetBits")
  require(crTargetBits >= 1 && crTargetBits <= originalBitWidth, s"Cr target bits must be between 1 and $originalBitWidth. Got $crTargetBits")

  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new PixelYCbCrBundle()))
    val out = Decoupled(new PixelYCbCrBundle())
    // val mode = Input(QuantizationMode()) // REMOVED: Mode is now set by constructor parameters
  })

  val y_quantized_reg  = Reg(UInt(originalBitWidth.W))
  val cb_quantized_reg = Reg(UInt(originalBitWidth.W))
  val cr_quantized_reg = Reg(UInt(originalBitWidth.W))
  val valid_reg        = RegInit(false.B)

  // Calculate shift amounts based on constructor parameters
  // These are Chisel literals, not UInts, as they are constant for a given instantiation
  val yShiftAmount_val  = originalBitWidth - yTargetBits
  val cbShiftAmount_val = originalBitWidth - cbTargetBits
  val crShiftAmount_val = originalBitWidth - crTargetBits
  
  io.in.ready := !valid_reg || io.out.ready

  when(io.in.fire) {
    val y_in = io.in.bits.y
    val cb_in = io.in.bits.cb
    val cr_in = io.in.bits.cr

    // Perform quantization: shift right to truncate, then shift left to restore magnitude
    // This effectively keeps the top N bits (targetBits) and zeros out the rest.
    y_quantized_reg  := (y_in >> yShiftAmount_val.U) << yShiftAmount_val.U
    cb_quantized_reg := (cb_in >> cbShiftAmount_val.U) << cbShiftAmount_val.U
    cr_quantized_reg := (cr_in >> crShiftAmount_val.U) << crShiftAmount_val.U
    
    valid_reg := true.B
  } .elsewhen(io.out.fire) {
    valid_reg := false.B
  }

  io.out.bits.y  := y_quantized_reg
  io.out.bits.cb := cb_quantized_reg
  io.out.bits.cr := cr_quantized_reg
  io.out.valid   := valid_reg
}
