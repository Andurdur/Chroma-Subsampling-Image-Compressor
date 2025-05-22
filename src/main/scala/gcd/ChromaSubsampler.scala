package jpeg

import chisel3._
import chisel3.util._
import PixelBundles._
/**
 * ChromaSubsampler supports 4:4:4, 4:2:2, 4:2:0, and 4:1:1 subsampling.
 * @param width   image width in pixels (must be even for horizontal subsampling)
 * @param height  image height in pixels (must be even for vertical subsampling where required)
 * @param mode    0->4:4:4, 1->4:2:2, 2->4:2:0, 3->4:1:1
 */
class ChromaSubsampler(width: Int, height: Int, mode: Int) extends Module {
  require(width > 0 && height > 0)
  val io = IO(new Bundle {
    val in   = Flipped(Decoupled(new PixelYCbCr))
    val out  = Decoupled(new PixelYCbCr)
    val sof  = Input(Bool())    
    val eol  = Input(Bool())    
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

  def sampleH: Bool = mode match {
    case 0 => true.B                       
    case 1 => (colCnt(0) === 0.U)         
    case 2 => (colCnt(0) === 0.U)         
    case 3 => (colCnt(1,0) === 0.U)        
  }
  def sampleV: Bool = mode match {
    case 0 => true.B                        
    case 1 => true.B                       
    case 2 => (rowCnt(0) === 0.U)          
    case 3 => true.B                        
  }
  val doSub = sampleH && sampleV
  val cbBuf0 = SyncReadMem(width, UInt(8.W))
  val cbBuf1 = SyncReadMem(width, UInt(8.W))
  val crBuf0 = SyncReadMem(width, UInt(8.W))
  val crBuf1 = SyncReadMem(width, UInt(8.W))
  val writeSel = RegInit(false.B)
  when(io.eol && io.in.fire()) { writeSel := ~writeSel }

  when(io.in.fire()) {
    val cbW = io.in.bits.cb
    val crW = io.in.bits.cr
    Mux(writeSel, cbBuf1, cbBuf0).write(colCnt, cbW)
    Mux(writeSel, crBuf1, crBuf0).write(colCnt, crW)
  }

  val cbRead = Mux(writeSel, cbBuf0, cbBuf1) 
  val crRead = Mux(writeSel, crBuf0, crBuf1)
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
  when(doSub) {
    mode match {
      case 0 => 
        cbSub := io.in.bits.cb
        crSub := io.in.bits.cr
      case 1 => 
        cbSub := (cbShift(1).asUInt + cbShift(0).asUInt + 1.U) >> 1
        crSub := (crShift(1).asUInt + crShift(0).asUInt + 1.U) >> 1
      case 2 => 
        val cbUp1 = cbBuf1.read(colCnt)
        val cbUp0 = cbBuf1.read(colCnt - 1.U)
        val crUp1 = crBuf1.read(colCnt)
        val crUp0 = crBuf1.read(colCnt - 1.U)
        val sumCb = cbShift(1).asUInt + cbShift(0).asUInt + cbUp1 + cbUp0
        val sumCr = crShift(1).asUInt + crShift(0).asUInt + crUp1 + crUp0
        cbSub := (sumCb + 2.U) >> 2
        crSub := (sumCr + 2.U) >> 2
      case 3 => 
        val cbSum = cbShift.foldLeft(0.U)((acc, x) => acc + x) 
        val crSum = crShift.foldLeft(0.U)((acc, x) => acc + x)
        cbSub := (cbSum + 1.U) >> 1 
        crSub := (crSum + 1.U) >> 1
    }
  }.otherwise {
    cbSub := 0.U
    crSub := 0.U
  }

  io.out.valid := io.in.valid && doSub
  io.in.ready  := io.out.ready

  io.out.bits.y  := io.in.bits.y
  io.out.bits.cb := cbSub
  io.out.bits.cr := crSub
}
