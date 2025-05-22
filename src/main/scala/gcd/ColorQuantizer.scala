package jpeg

import chisel3._
import chisel3.util._
import PixelBundles._

class ColorQuantizer extends Module {
  val paletteSize = 64
  val idxWidth = log2Ceil(paletteSize)

  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new PixelYCbCr))
    val out = Decoupled(new PixelYCbCr)
  })

  val levels = Seq(0.U(8.W), 85.U(8.W), 170.U(8.W), 255.U(8.W))
  val palette = VecInit(
    for {
      r <- levels
      g <- levels
      b <- levels
    } yield {
      val p = Wire(new PixelYCbCr)
      val rS = r.asSInt; val gS = g.asSInt; val bS = b.asSInt
      val yI  = ((  77.S * rS + 150.S * gS +  29.S * bS + 128.S) >> 8).asUInt
      val cbI = (((-43.S * rS -  85.S * gS + 128.S * bS + 128.S) >> 8) + 128.S).asUInt
      val crI = (((128.S * rS - 107.S * gS -  21.S * bS + 128.S) >> 8) + 128.S).asUInt
      p.y  := yI
      p.cb := cbI
      p.cr := crI
      p
    }
  )

  io.in.ready := io.out.ready
  io.out.valid := io.in.valid

  val diffs = Seq.tabulate(paletteSize) { i =>
    val pal = palette(i)
    val dy  = (io.in.bits.y.asSInt  - pal.y.asSInt).abs.asUInt
    val dcb = (io.in.bits.cb.asSInt - pal.cb.asSInt).abs.asUInt
    val dcr = (io.in.bits.cr.asSInt - pal.cr.asSInt).abs.asUInt
    dy + dcb + dcr
  }
  val distVec = VecInit(diffs)

  val (minDist, minIdx) = distVec.zipWithIndex.foldLeft((distVec(0), 0.U(idxWidth.W))) {
    case ((curMin, curIdx), (d, i)) =>
      val better = d < curMin
      val newMin = Mux(better, d, curMin)
      val newIdx = Mux(better, i.U, curIdx)
      (newMin, newIdx)
  }

  io.out.bits := palette(minIdx)
}
