package Chroma_Subsampling_Image_Compressor

import chisel3._
import chisel3.util._

class ChromaSubsampler(
    val imageWidth: Int,
    val imageHeight: Int,
    val bitWidth: Int, // Bit width of Y, Cb, Cr components (typically 8)
    val param_a: Int,  // Horizontal sampling reference (4, 2, or 1 for J=4)
    val param_b: Int   // Vertical sampling reference (usually 'a' or 0)
) extends Module {
  require(imageWidth > 0, "Image width must be positive")
  require(imageHeight > 0, "Image height must be positive")
  require(bitWidth == 8, "This version assumes bitWidth is 8 to match PixelYCbCrBundle.")
  
  require(Seq(4, 2, 1).contains(param_a), s"param_a must be 4, 2, or 1. Got $param_a")
  require(param_b == param_a || param_b == 0, s"param_b must be equal to param_a ($param_a) or 0. Got $param_b")

  val io = IO(new Bundle {
    val dataIn = Flipped(Decoupled(new PixelYCbCrBundle()))
    val dataOut = Decoupled(new PixelYCbCrBundle())
  })

  // Derive sampling factors from J:a:b parameters (J is fixed at 4)
  val horizontalCbCrSamplingFactor = 4 / param_a
  val verticalCbCrSamplingFactor   = if (param_b == 0 && param_a != 0) 2 else 1 // If b=0, vertical factor is 2, else 1

  val yReg = Reg(UInt(bitWidth.W))
  val cbReg = Reg(UInt(bitWidth.W))
  val crReg = Reg(UInt(bitWidth.W))
  val validReg = RegInit(false.B)

  val lastCbReg = RegInit(0.U(bitWidth.W))
  val lastCrReg = RegInit(0.U(bitWidth.W))

  val (pixelCounter, pixelWrap) = Counter(io.dataIn.fire, imageWidth)
  val (lineCounter, lineWrap)   = Counter(io.dataIn.fire && pixelWrap, imageHeight)

  io.dataIn.ready := !validReg || io.dataOut.ready

  io.dataOut.bits.y  := yReg
  io.dataOut.bits.cb := cbReg
  io.dataOut.bits.cr := crReg
  io.dataOut.valid   := validReg

  when(io.dataIn.fire) {
    yReg := io.dataIn.bits.y
    validReg := true.B

    // Determine if the current pixel is a chroma sampling point
    val sampleCbCrHorizontally = (pixelCounter % horizontalCbCrSamplingFactor.U) === 0.U
    val sampleCbCrVertically   = (lineCounter  % verticalCbCrSamplingFactor.U)   === 0.U
    
    val isChromaSamplePoint = sampleCbCrHorizontally && sampleCbCrVertically

    when(isChromaSamplePoint) {
      cbReg     := io.dataIn.bits.cb
      crReg     := io.dataIn.bits.cr
      lastCbReg := io.dataIn.bits.cb // Update stored chroma if it's a sample point
      lastCrReg := io.dataIn.bits.cr
    } .otherwise {
      cbReg     := lastCbReg // Use previously sampled/stored chroma
      crReg     := lastCrReg
    }
  } .elsewhen(io.dataOut.fire) {
    validReg := false.B
  }
}
