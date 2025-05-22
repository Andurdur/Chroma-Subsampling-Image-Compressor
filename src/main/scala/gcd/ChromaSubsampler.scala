package jpeg

import chisel3._
import chisel3.util._
import PixelBundles._

class ChromaSubsampler(width: Int, height: Int, mode: Int) extends Module {
  require(width > 0 && height > 0)
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new PixelYCbCr))
    val out = Decoupled(new PixelYCbCr)
    val sof = Input(Bool())
    val eol = Input(Bool())
  })

  val colCnt = RegInit(0.U(log2Ceil(width).W))
  val rowCnt = RegInit(0.U(log2Ceil(height).W))
  when(io.in.fire()) {
    when(io.eol) {
      colCnt := 0.U
      rowCnt := rowCnt + 1.U
    }.otherwise {
      colCnt := colCnt + 1.U
    }
  }
  when(io.sof) {
    rowCnt := 0.U
  }

  val sampleH = mode match {
    case 0 => true.B
    case 1 => colCnt(0) === 0.U
    case 2 => colCnt(0) === 0.U
    case 3 => colCnt(1,0) === 0.U
  }
  val sampleV = mode match {
    case 0 => true.B
    case 1 => true.B
    case 2 => rowCnt(0) === 0.U
    case 3 => true.B
  }
  val doSample = io.in.valid && sampleH && sampleV

  val cbBuf0 = SyncReadMem(width, UInt(8.W))
  val cbBuf1 = SyncReadMem(width, UInt(8.W))
  val crBuf0 = SyncReadMem(width, UInt(8.W))
  val crBuf1 = SyncReadMem(width, UInt(8.W))

  val writeSel = RegInit(false.B)
  when(io.in.fire() && io.eol) {
    writeSel := ~writeSel
  }

  when(io.in.fire()) {
    if (writeSel.litValue == 0) {
      cbBuf0.write(colCnt, io.in.bits.cb)
      crBuf0.write(colCnt, io.in.bits.cr)
    } else {
      cbBuf1.write(colCnt, io.in.bits.cb)
      crBuf1.write(colCnt, io.in.bits.cr)
    }
  }

  val cbPrev = Mux(writeSel, cbBuf1, cbBuf0)
  val crPrev = Mux(writeSel, crBuf1, crBuf0)

  val cbUp1 = cbPrev.read(colCnt)
  val cbUp0 = cbPrev.read(colCnt - 1.U)
  val crUp1 = crPrev.read(colCnt)
  val crUp0 = crPrev.read(colCnt - 1.U)

  val cbShift = Reg(Vec(2, UInt(8.W)))
  val crShift = Reg(Vec(2, UInt(8.W)))
  when(io.in.fire()) {
    cbShift(0) := cbShift(1)
    cbShift(1) := io.in.bits.cb
    crShift(0) := crShift(1)
    crShift(1) := io.in.bits.cr
  }

  val cbSub = Wire(UInt(8.W))
  val crSub = Wire(UInt(8.W))
  when(doSample) {
    switch(mode.U) {
      is(0.U) {
        cbSub := io.in.bits.cb
        crSub := io.in.bits.cr
      }
      is(1.U) {
        cbSub := (cbShift(1) + cbShift(0) + 1.U) >> 1
        crSub := (crShift(1) + crShift(0) + 1.U) >> 1
      }
      is(2.U) {
        val sumCb = cbShift(0) + cbShift(1) + cbUp0 + cbUp1
        val sumCr = crShift(0) + crShift(1) + crUp0 + crUp1
        cbSub := (sumCb + 2.U) >> 2
        crSub := (sumCr + 2.U) >> 2
      }
      is(3.U) {
        val sumCb = cbShift(0) + cbShift(1)
        val sumCr = crShift(0) + crShift(1)
        cbSub := (sumCb + 1.U) >> 1
        crSub := (sumCr + 1.U) >> 1
      }
    }
  }.otherwise {
    cbSub := 0.U
    crSub := 0.U
  }

  io.out.valid := doSample
  io.in.ready := io.out.ready
  io.out.bits.y := io.in.bits.y
  io.out.bits.cb := cbSub
  io.out.bits.cr := crSub
}
