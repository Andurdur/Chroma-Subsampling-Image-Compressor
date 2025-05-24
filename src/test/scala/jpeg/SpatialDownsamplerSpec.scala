package jpeg

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SpatialDownsamplerSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "SpatialDownsampler"

  it should "downsample a 4×4 YCbCr image by factor 2" in {
    test(new SpatialDownsampler(4, 4, 2)) { dut =>
      dut.io.out.ready.poke(true.B)
      var inIdx = 0
      var outCount = 0
      val totalIn = 16
      val expected = Seq(0, 2, 8, 10)

      while (outCount < expected.size) {
        // feed inputs whenever ready
        dut.io.in.valid.poke(inIdx < totalIn && dut.io.in.ready.peek().litToBoolean)
        if (dut.io.in.valid.peek().litToBoolean) {
          dut.io.in.bits.y.poke(inIdx.U)
          dut.io.in.bits.cb.poke((100 + inIdx).U)
          dut.io.in.bits.cr.poke((200 + inIdx).U)
          inIdx += 1
        }
        // capture outputs
        if (dut.io.out.valid.peek().litToBoolean) {
          val exp = expected(outCount)
          dut.io.out.bits.y.peek().litValue.toInt shouldBe exp
          dut.io.out.bits.cb.peek().litValue.toInt shouldBe (100 + exp)
          dut.io.out.bits.cr.peek().litValue.toInt shouldBe (200 + exp)
          outCount += 1
        }
        dut.clock.step()
      }
    }
  }

  it should "handle back-pressure correctly" in {
    test(new SpatialDownsampler(4, 4, 2)) { dut =>
      // When output is not ready, input should stall
      dut.io.out.ready.poke(false.B)
      dut.clock.step()
      dut.io.in.ready.peek().litToBoolean shouldBe false

      // Now allow output, input should resume
      dut.io.out.ready.poke(true.B)
      dut.clock.step()
      dut.io.in.ready.peek().litToBoolean shouldBe true
    }
  }

  it should "support factor 4 on 8×8 data" in {
    val size = 8
    val expected = (for {
      row <- 0 until size if row % 4 == 0
      col <- 0 until size if col % 4 == 0
    } yield row * size + col).toSeq

    test(new SpatialDownsampler(size, size, 4)) { dut =>
      dut.io.out.ready.poke(true.B)
      var inIdx = 0
      var outCount = 0
      val totalIn = size * size

      while (outCount < expected.size) {
        dut.io.in.valid.poke(inIdx < totalIn && dut.io.in.ready.peek().litToBoolean)
        if (dut.io.in.valid.peek().litToBoolean) {
          dut.io.in.bits.y.poke(inIdx.U)
          dut.io.in.bits.cb.poke((inIdx * 2).U)
          dut.io.in.bits.cr.poke((inIdx * 3).U)
          inIdx += 1
        }
        if (dut.io.out.valid.peek().litToBoolean) {
          dut.io.out.bits.y.peek().litValue.toInt shouldBe expected(outCount)
          outCount += 1
        }
        dut.clock.step()
      }
    }
  }

  it should "support factor 8 on 16×16 data" in {
    val size = 16
    val expected = (for {
      row <- 0 until size if row % 8 == 0
      col <- 0 until size if col % 8 == 0
    } yield row * size + col).toSeq

    test(new SpatialDownsampler(size, size, 8)) { dut =>
      dut.io.out.ready.poke(true.B)
      var inIdx = 0
      var outCount = 0
      val totalIn = size * size

      while (outCount < expected.size) {
        dut.io.in.valid.poke(inIdx < totalIn && dut.io.in.ready.peek().litToBoolean)
        if (dut.io.in.valid.peek().litToBoolean) {
          dut.io.in.bits.y.poke(inIdx.U)
          dut.io.in.bits.cb.poke((inIdx + 1).U)
          dut.io.in.bits.cr.poke((inIdx + 2).U)
          inIdx += 1
        }
        if (dut.io.out.valid.peek().litToBoolean) {
          dut.io.out.bits.y.peek().litValue.toInt shouldBe expected(outCount)
          outCount += 1
        }
        dut.clock.step()
      }
    }
  }

  it should "handle non-power-of-two dimensions" in {
    val width = 5; val height = 3; val factor = 2
    val expected = Seq(0, 2, 4, 10, 12, 14)

    test(new SpatialDownsampler(width, height, factor)) { dut =>
      dut.io.out.ready.poke(true.B)
      var inIdx = 0
      var outCount = 0
      val totalIn = width * height

      while (outCount < expected.size) {
        dut.io.in.valid.poke(inIdx < totalIn && dut.io.in.ready.peek().litToBoolean)
        if (dut.io.in.valid.peek().litToBoolean) {
          dut.io.in.bits.y.poke(inIdx.U)
          dut.io.in.bits.cb.poke((10 + inIdx).U)
          dut.io.in.bits.cr.poke((20 + inIdx).U)
          inIdx += 1
        }
        if (dut.io.out.valid.peek().litToBoolean) {
          dut.io.out.bits.y.peek().litValue.toInt shouldBe expected(outCount)
          outCount += 1
        }
        dut.clock.step()
      }
    }
  }

  it should "reject unsupported factors" in {
    intercept[IllegalArgumentException] {
      new SpatialDownsampler(4, 4, 3)
    }
  }
}
