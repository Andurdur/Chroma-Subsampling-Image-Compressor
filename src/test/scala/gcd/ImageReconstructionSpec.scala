package jpeg

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class ImageReconstructionSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "ImageCompressorTop"

  it should "dump YCbCr to ycbcr_out.bin" in {
    test(new ImageCompressorTop(512, 512, subMode = 2, downFactor = 2)) { dut =>
      val fos = new java.io.FileOutputStream("ycbcr_out.bin")
      val W = 512; val H = 512
      val totalOut = (W/2) * (H/2)
      var inCount = 0
      var outCount = 0

      dut.io.out.ready.poke(true.B)
      dut.io.in.valid.poke(false.B)

      while (outCount < totalOut) {
        if (inCount < W * H && dut.io.in.ready.peek().litToBoolean) {
          dut.io.in.bits.r.poke(0.U)
          dut.io.in.bits.g.poke(0.U)
          dut.io.in.bits.b.poke(0.U)
          dut.io.in.valid.poke(true.B)
          inCount += 1
        } else {
          dut.io.in.valid.poke(false.B)
        }
        
        if (dut.io.out.valid.peek().litToBoolean) {
          val y  = dut.io.out.bits.y.peek().litValue.toByte
          val cb = dut.io.out.bits.cb.peek().litValue.toByte
          val cr = dut.io.out.bits.cr.peek().litValue.toByte
          fos.write(Array(y, cb, cr))
          outCount += 1
        }
        dut.clock.step()
      }
      fos.close()
    }
  }
}
