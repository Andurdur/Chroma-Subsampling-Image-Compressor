package jpeg

import chisel3._
import chisel3.tester._
import chisel3.util._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SpatialDownsamplerSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  "SpatialDownsampler" should "downsample a 4x4 YCbCr image by factor 2" in {
    test(new SpatialDownsampler(4, 4, 2)) { dut =>
      // Generate a 4x4 identity pattern: pixel value = row*4 + col
      val inPixels = (0 until 16).map(i => {
        val y  = (i & 0xFF).U
        val cb = (128 + i).U  // arbitrary offset
        val cr = (64 + i).U   // arbitrary offset
        (y, cb, cr)
      })

      // Drive inputs
      fork {
        for ((y, cb, cr) <- inPixels) {
          dut.io.in.bits.y.poke(y)
          dut.io.in.bits.cb.poke(cb)
          dut.io.in.bits.cr.poke(cr)
          dut.io.in.valid.poke(true.B)
          dut.clock.step()
        }
        dut.io.in.valid.poke(false.B)
      }.fork {
        // Read downsampled outputs: expect 4 outputs (every 2nd pixel on 2x2 grid)
        val got = scala.collection.mutable.ArrayBuffer[(Int,Int,Int)]()
        while (got.size < 4) {
          if (dut.io.out.valid.peek().litToBoolean) {
            got += ((dut.io.out.bits.y.peek().litValue.toInt,
                     dut.io.out.bits.cb.peek().litValue.toInt,
                     dut.io.out.bits.cr.peek().litValue.toInt))
            dut.io.out.ready.poke(true.B)
          }
          dut.clock.step()
        }
        // Check expected positions: (0,0), (0,2), (2,0), (2,2)
        val expIndices = Seq(0, 2, 8, 10)
        got.zip(expIndices).foreach { case ((y,cb,cr), idx) =>
          y   shouldBe idx
          cb  shouldBe (128 + idx)
          cr  shouldBe (64 + idx)
        }
      }.join()
    }
  }
}