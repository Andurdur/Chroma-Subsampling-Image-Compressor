package jpeg

import chisel3._
import chisel3.util._

class SpatialDownsampler(width: Int, height: Int, factor: Int) extends Module {
  require(width > 0 && height > 0)
  require(factor == 2 || factor == 4 || factor == 8)

  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new PixelYCbCr))
    val out = Decoupled(new PixelYCbCr)
  })

  val colCnt = RegInit(0.U(log2Ceil(width).W))
  val rowCnt = RegInit(0.U(log2Ceil(height).W))

  when(io.in.fire) {
    when(colCnt === (width-1).U) {
      colCnt := 0.U
      rowCnt := rowCnt + 1.U
    }.otherwise {
      colCnt := colCnt + 1.U
    }
  }

  val sampleH = factor match {
    case 2 => colCnt(0) === 0.U
    case 4 => colCnt(1,0) === 0.U
    case 8 => colCnt(2,0) === 0.U
  }
  val sampleV = factor match {
    case 2 => rowCnt(0) === 0.U
    case 4 => rowCnt(1,0) === 0.U
    case 8 => rowCnt(2,0) === 0.U
  }
  val doSample = io.in.valid && sampleH && sampleV

  io.out.valid := doSample
  io.in.ready  := io.out.ready

  io.out.bits := Mux(doSample, io.in.bits, WireInit(0.U.asTypeOf(new PixelYCbCr)))
}
