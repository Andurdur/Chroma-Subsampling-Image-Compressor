package jpeg

import chisel3._
import chisel3.testers.BasicTester
import chisel3.tester._
import chisel3.util._
import java.io._

class ImageReconstructionSpec extends AnyFlatSpec with ChiselScalatestTester {
  "ImageCompressorTop" should "dump YCbCr to ycbcr_out.bin" in {
    test(new ImageCompressorTop(512, 512, 2, 2)) { dut =>
      val fos = new FileOutputStream("ycbcr_out.bin")
      val totalPixels = (512/2)*(512/2) 
      var seen = 0
      while (seen < totalPixels) {
        dut.clock.step()
        if (dut.io.out.valid.peek().litToBoolean) {
          val y  = dut.io.out.bits.y.peek().litValue.toByte
          val cb = dut.io.out.bits.cb.peek().litValue.toByte
          val cr = dut.io.out.bits.cr.peek().litValue.toByte
          fos.write(Array(y, cb, cr))
          seen += 1
        }
      }
      fos.close()
    }
  }
}
