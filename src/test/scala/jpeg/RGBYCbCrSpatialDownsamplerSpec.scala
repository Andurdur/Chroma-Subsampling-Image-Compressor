package jpeg

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import PixelBundles._

class RGBYCbCrSpatialDownsamplerSpec extends AnyFlatSpec with ChiselScalatestTester {
  "RGB2YCbCr + SpatialDownsampler" should "convert RGB=0 to YCbCr and downsample" in {
    test(new Module {
      val io = IO(new Bundle {
        val in  = Flipped(Decoupled(new PixelRGB))
        val out = Decoupled(new PixelYCbCr)
      })
      val conv = Module(new RGB2YCbCr)
      val spat = Module(new SpatialDownsampler(4, 4, 2))
      io.in <> conv.io.in
      conv.io.out <> spat.io.in
      spat.io.sof := false.B
      spat.io.eol := false.B
      io.out <> spat.io.out
    }) { dut =>
      val totalPixels = 16
      val expectedOutputs = 4
      var inCount = 0
      var outCount = 0

      dut.io.out.ready.poke(true.B)
      dut.io.in.valid.poke(false.B)

      while (outCount < expectedOutputs) {
        if (inCount < totalPixels && dut.io.in.ready.peek().litToBoolean) {
          dut.io.in.bits.r.poke(0.U)
          dut.io.in.bits.g.poke(0.U)
          dut.io.in.bits.b.poke(0.U)
          dut.io.in.valid.poke(true.B)
          inCount += 1
        } else {
          dut.io.in.valid.poke(false.B)
        }

        if (dut.io.out.valid.peek().litToBoolean) {
          dut.io.out.bits.y.peek().litValue should be (0)
          dut.io.out.bits.cb.peek().litValue should be (128)
          dut.io.out.bits.cr.peek().litValue should be (128)
          outCount += 1
        }
        dut.clock.step()
      }
    }
  }
}
