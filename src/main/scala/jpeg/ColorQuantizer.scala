package Chroma_Subsampling_Image_Compressor 

import chisel3._
import chisel3.util._


object QuantizationMode extends ChiselEnum {
  val Q_24BIT, // Effectively 8-8-8 YCbCr (no quantization)
  Q_16BIT, // Effective Y:6, Cb:5, Cr:5
  Q_8BIT   // Effective Y:3, Cb:3, Cr:2
  = Value
}

class ColorQuantizer(originalBitWidth: Int = 8) extends Module {
  require(originalBitWidth > 0 && originalBitWidth <= 8, "Original bit width must be > 0 and <= 8 for this example")

  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new PixelYCbCrBundle()))
    val out = Decoupled(new PixelYCbCrBundle())
    val mode = Input(QuantizationMode())
  })

  val y_quantized_reg  = Reg(UInt(originalBitWidth.W))
  val cb_quantized_reg = Reg(UInt(originalBitWidth.W))
  val cr_quantized_reg = Reg(UInt(originalBitWidth.W))
  val valid_reg        = RegInit(false.B)

  val yTargetEffectiveBits = Wire(UInt(4.W))
  val cbTargetEffectiveBits = Wire(UInt(4.W))
  val crTargetEffectiveBits = Wire(UInt(4.W))

  // Pre-assign default values (e.g., passthrough/24-bit mode)
  yTargetEffectiveBits  := originalBitWidth.U
  cbTargetEffectiveBits := originalBitWidth.U
  crTargetEffectiveBits := originalBitWidth.U

  switch(io.mode) {
    is(QuantizationMode.Q_24BIT) {
      yTargetEffectiveBits  := originalBitWidth.U // 8.U if originalBitWidth is 8
      cbTargetEffectiveBits := originalBitWidth.U
      crTargetEffectiveBits := originalBitWidth.U
    }
    is(QuantizationMode.Q_16BIT) { // Y:6, Cb:5, Cr:5
      yTargetEffectiveBits  := 6.U
      cbTargetEffectiveBits := 5.U
      crTargetEffectiveBits := 5.U
    }
    is(QuantizationMode.Q_8BIT) {  // Y:3, Cb:3, Cr:2
      yTargetEffectiveBits  := 3.U
      cbTargetEffectiveBits := 3.U
      crTargetEffectiveBits := 2.U
    }
  }

  val yShiftAmount  = Wire(UInt(4.W))
  val cbShiftAmount = Wire(UInt(4.W))
  val crShiftAmount = Wire(UInt(4.W))

  yShiftAmount  := originalBitWidth.U - yTargetEffectiveBits
  cbShiftAmount := originalBitWidth.U - cbTargetEffectiveBits
  crShiftAmount := originalBitWidth.U - crTargetEffectiveBits
  
  io.in.ready := !valid_reg || io.out.ready

  when(io.in.fire) {
    val y_in = io.in.bits.y
    val cb_in = io.in.bits.cb
    val cr_in = io.in.bits.cr

    y_quantized_reg  := (y_in >> yShiftAmount) << yShiftAmount
    cb_quantized_reg := (cb_in >> cbShiftAmount) << cbShiftAmount
    cr_quantized_reg := (cr_in >> crShiftAmount) << crShiftAmount
    
    valid_reg := true.B
  } .elsewhen(io.out.fire) {
    valid_reg := false.B
  }

  io.out.bits.y  := y_quantized_reg
  io.out.bits.cb := cb_quantized_reg
  io.out.bits.cr := cr_quantized_reg
  io.out.valid   := valid_reg


}
