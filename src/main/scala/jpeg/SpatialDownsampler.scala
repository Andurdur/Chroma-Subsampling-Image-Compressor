package Chroma_Subsampling_Image_Compressor

import chisel3._
import chisel3.util._

// Assumes PixelYCbCrBundle is defined (e.g., by user, with y, cb, cr fields)
// class PixelYCbCrBundle extends Bundle { val y=UInt(8.W); val cb=UInt(8.W); val cr=UInt(8.W); }


class SpatialDownsampler(width: Int, height: Int, factor: Int) extends Module {
  require(width > 0 && height > 0, "Width and height must be positive")
  require(factor == 1 || factor == 2 || factor == 4 || factor == 8, "Factor must be 1, 2, 4, or 8")

  val io = IO(new Bundle {
    val sof = Input(Bool())
    val eol = Input(Bool())
    val in  = Flipped(Decoupled(new PixelYCbCrBundle)) // User's bundle
    val out = Decoupled(new PixelYCbCrBundle)   // User's bundle
  })

  val colCnt = RegInit(0.U(log2Ceil(width).W))
  val rowCnt = RegInit(0.U(log2Ceil(height).W))

  when(io.in.fire) {
    when(colCnt === (width - 1).U) {
      colCnt := 0.U
      when(rowCnt === (height - 1).U) {
        rowCnt := 0.U
      } .otherwise {
        rowCnt := rowCnt + 1.U
      }
    } .otherwise {
      colCnt := colCnt + 1.U
    }
  }

  val sampleH: Bool = if (factor == 1) true.B
                      else if (factor == 2) (colCnt(0) === 0.U)
                      else if (factor == 4) (colCnt(1,0) === 0.U)
                      else if (factor == 8) (colCnt(2,0) === 0.U)
                      else {assert(false.B, "Invalid factor for sampleH"); false.B}

  val sampleV: Bool = if (factor == 1) true.B
                      else if (factor == 2) (rowCnt(0) === 0.U)
                      else if (factor == 4) (rowCnt(1,0) === 0.U)
                      else if (factor == 8) (rowCnt(2,0) === 0.U)
                      else {assert(false.B, "Invalid factor for sampleV"); false.B}

  val doSample = sampleH && sampleV

  io.out.valid := io.in.valid && doSample
  
  when (doSample) {
    io.in.ready := io.out.ready
  } .otherwise {
    io.in.ready := true.B 
  }

  io.out.bits := io.in.bits

  // This internal printf is useful, keep it if you want.
  when(io.in.valid) { // Print when input is valid to see its state, regardless of fire
     printf(p"SpatialDownsampler: factor=$factor In(valid=${io.in.valid} ready=${io.in.ready}) Pos(col=${colCnt},row=${rowCnt}) SampleH=${sampleH} SampleV=${sampleV} DoSample=${doSample} -> Out(valid=${io.out.valid} ready=${io.out.ready})\n")
  }
}
