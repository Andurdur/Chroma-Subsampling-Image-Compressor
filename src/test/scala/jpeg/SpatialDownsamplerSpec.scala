package jpeg

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

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

  it should "downsample a 16×16 PNG to 8×8 and write it out" in {
    // read a 16×16 input image
    val inImg: BufferedImage = ImageIO.read(new File("test_images/in16x16.png"))
    require(inImg.getWidth == 16 && inImg.getHeight == 16)

    test(new SpatialDownsampler(16, 16, 2)) { dut =>
      dut.io.out.ready.poke(true.B)
      // optional frame signals
      dut.io.sof.poke(true.B); dut.io.eol.poke(false.B)
      dut.clock.step()
      dut.io.sof.poke(false.B)

      // feed in pixels row-major
      for (y <- 0 until 16; x <- 0 until 16) {
        val rgb = inImg.getRGB(x, y)
        val r = (rgb >> 16) & 0xFF
        val g = (rgb >> 8)  & 0xFF
        val b = (rgb      ) & 0xFF
        val (yy, cb, cr) = ReferenceModel.rgb2ycbcr(r, g, b)

        dut.io.in.bits.y.poke(yy.U)
        dut.io.in.bits.cb.poke(cb.U)
        dut.io.in.bits.cr.poke(cr.U)
        dut.io.in.valid.poke(true.B)
        dut.clock.step()
      }
      dut.io.in.valid.poke(false.B)

      // collect 8×8 outputs
      val outPixels = Array.fill[(Int,Int,Int)](64)((0,0,0))
      var idx = 0
      while (idx < 64) {
        if (dut.io.out.valid.peek().litToBoolean) {
          val yv  = dut.io.out.bits.y.peek().litValue.toInt
          val cbv = dut.io.out.bits.cb.peek().litValue.toInt
          val crv = dut.io.out.bits.cr.peek().litValue.toInt
          outPixels(idx) = (yv, cbv, crv)
          idx += 1
        }
        dut.clock.step()
      }

      // write to 8×8 PNG
      val outImg = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB)
      for ((pixel, i) <- outPixels.zipWithIndex) {
        val (yy, cb, cr) = pixel
        val (r2, g2, b2) = ReferenceModel.ycbcr2rgb(yy, cb, cr)
        val rgb2 = (r2 << 16) | (g2 << 8) | b2
        val ox = i % 8
        val oy = i / 8
        outImg.setRGB(ox, oy, rgb2)
      }
      ImageIO.write(outImg, "png", new File("test_images/out8x8.png"))
    }
  }
}
