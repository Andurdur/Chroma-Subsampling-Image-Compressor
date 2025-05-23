package jpeg

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SpatialDownsamplerSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  "SpatialDownsampler" should "downsample a 4×4 YCbCr image by factor 2" in {
    test(new SpatialDownsampler(4, 4, 2)) { dut =>
      dut.io.out.ready.poke(true.B)
      var inCount  = 0
      var outCount = 0
      val totalIn     = 16
      val expectedOut = 4

      while (outCount < expectedOut) {
        if (inCount < totalIn && dut.io.in.ready.peek().litToBoolean) {
          // generate a simple ramp
          dut.io.in.bits.y.poke(inCount)
          dut.io.in.bits.cb.poke(128 + inCount)
          dut.io.in.bits.cr.poke(64  + inCount)
          dut.io.in.valid.poke(true.B)
          inCount += 1
        } else {
          dut.io.in.valid.poke(false.B)
        }

        if (dut.io.out.valid.peek().litToBoolean) {
          // We expect outputs at indices 0, 2, 8, 10
          val expIdx = outCount match {
            case 0 => 0
            case 1 => 2
            case 2 => 8
            case 3 => 10
          }
          dut.io.out.bits.y.peek().litValue.toInt  shouldBe expIdx
          dut.io.out.bits.cb.peek().litValue.toInt shouldBe 128 + expIdx
          dut.io.out.bits.cr.peek().litValue.toInt shouldBe 64  + expIdx
          outCount += 1
        }

        dut.clock.step()
      }
    }
  }
}
