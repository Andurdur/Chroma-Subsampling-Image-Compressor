package jpeg

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import PixelBundle._

class SpatialDownsamplerSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  "SpatialDownsampler" should "downsample a 4x4 YCbCr image by factor 2" in {
    test(new SpatialDownsampler(4, 4, 2)) { dut =>
      val inPixels = (0 until 16).map(i => (i.U(8.W), (128 + i).U(8.W), (64 + i).U(8.W)))
      dut.io.out.ready.poke(true.B)
      var inCount = 0
      var outCount = 0

      while (outCount < 4) {
        if (inCount < inPixels.size && dut.io.in.ready.peek().litToBoolean) {
          val (y, cb, cr) = inPixels(inCount)
          dut.io.in.bits.y.poke(y)
          dut.io.in.bits.cb.poke(cb)
          dut.io.in.bits.cr.poke(cr)
          dut.io.in.valid.poke(true.B)
          inCount += 1
        } else {
          dut.io.in.valid.poke(false.B)
        }

        if (dut.io.out.valid.peek().litToBoolean) {
          val (yExp, cbExp, crExp) = inPixels((outCount * 2) * 4 + (outCount * 2))
          dut.io.out.bits.y.peek().litValue.toInt shouldBe yExp.litValue.toInt
          dut.io.out.bits.cb.peek().litValue.toInt shouldBe cbExp.litValue.toInt
          dut.io.out.bits.cr.peek().litValue.toInt shouldBe crExp.litValue.toInt
          outCount += 1
        }
        dut.clock.step()
      }
    }
  }
}
