package jpeg 

import chisel3._
import chisel3.util._


import Chroma_Subsampling_Image_Compressor.{PixelBundle, PixelYCbCrBundle}

class RGB2YCbCr extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new PixelBundle))
    val out = Decoupled(new PixelYCbCrBundle)
  })

  val R = io.in.bits.r
  val G = io.in.bits.g
  val B = io.in.bits.b


  val C77         = 77.S(8.W)
  val C150        = 150.S(9.W)
  val C29         = 29.S(8.W)
  val C_43        = -43.S(8.W)
  val C_85        = -85.S(8.W)
  val C128_coeff  = 128.S(9.W) 
  val C_107       = -107.S(8.W)
  val C_21        = -21.S(8.W)
  
  val C128_bias   = 128.S(9.W) 
 
  val C128_offset = 128.S(9.W) 

  val y_int  = (C77 * R.zext) + (C150 * G.zext) + (C29 * B.zext)
  val cb_int = (C_43 * R.zext) + (C_85 * G.zext) + (C128_coeff * B.zext)
  val cr_int = (C128_coeff * R.zext) + (C_107 * G.zext) + (C_21 * B.zext)

  def clampToUInt8(value: SInt): UInt = {
    val clamped_val = Wire(SInt(value.getWidth.W))
    when(value < 0.S) {
      clamped_val := 0.S
    }.elsewhen(value > 255.S) {
      clamped_val := 255.S
    }.otherwise {
      clamped_val := value
    }
    clamped_val.asUInt
  }
  

  def divideBy256_floor(numerator: SInt): SInt = {
    numerator >> 8
  }

  // Intermediate values after division and offset
  val y_temp_intermediate  = y_int + C128_bias 
  val y_temp  = Wire(SInt(9.W))
  y_temp := divideBy256_floor(y_temp_intermediate) // Using floor division

  val cb_temp_intermediate = cb_int + C128_bias
  val cb_temp = Wire(SInt(10.W)) 
  cb_temp := divideBy256_floor(cb_temp_intermediate) + C128_offset // Using floor division
  
  val cr_temp_intermediate = cr_int + C128_bias
  val cr_temp = Wire(SInt(10.W))
  cr_temp := divideBy256_floor(cr_temp_intermediate) + C128_offset // Using floor division

  val y_final  = Reg(UInt(8.W))
  val cb_final = Reg(UInt(8.W))
  val cr_final = Reg(UInt(8.W))
  
  val valid_reg = RegInit(false.B)

  io.in.ready := !valid_reg || io.out.ready
  val y_clamped_for_output = clampToUInt8(y_temp)
  val cb_clamped_for_output = clampToUInt8(cb_temp)
  val cr_clamped_for_output = clampToUInt8(cr_temp)

  when(io.in.fire) {
    y_final   := y_clamped_for_output
    cb_final  := cb_clamped_for_output
    cr_final  := cr_clamped_for_output
    valid_reg := true.B
  }.elsewhen(io.out.fire) {
    valid_reg := false.B

  }.otherwise{
  }
  io.out.bits.y  := y_final
  io.out.bits.cb := cb_final
  io.out.bits.cr := cr_final
  io.out.valid   := valid_reg
}

object YCbCrUtils {
  def rgbToYCbCr(r_in: Int, g_in: Int, b_in: Int): (Int, Int, Int) = {
    val R_val = r_in
    val G_val = g_in
    val B_val = b_in

    val yInt  =  77 * R_val + 150 * G_val +  29 * B_val
    val cbInt = -43 * R_val -  85 * G_val + 128 * B_val
    val crInt = 128 * R_val - 107 * G_val -  21 * B_val

    def clampInt(value: Int): Int = {
      if (value < 0) 0
      else if (value > 255) 255
      else value
    }
    

    val y_div_intermediate = (yInt  + 128) / 256
    val cb_div_intermediate = (cbInt + 128) / 256
    val cr_div_intermediate = (crInt + 128) / 256


    val y_final  = clampInt(y_div_intermediate + 0) // Y is not offset by +128 in the end
    val cb_final = clampInt(cb_div_intermediate + 128)
    val cr_final = clampInt(cr_div_intermediate + 128)

    (y_final, cb_final, cr_final)
  }

  def ycbcr2rgb(y: Int, cb: Int, cr: Int): (Int, Int, Int) = {
    val c_val = y 
    val d_val = cb - 128
    val e_val = cr - 128
    def clamp(v: Int): Int = math.max(0, math.min(255, v))
    val r = clamp((298 * c_val + 409 * e_val + 128) >> 8)
    val g = clamp((298 * c_val - 100 * d_val - 208 * e_val + 128) >> 8)
    val b = clamp((298 * c_val + 516 * d_val + 128) >> 8)
    (r, g, b)
  }
}
