package jpeg 

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.MutableImage
import com.sksamuel.scrimage.pixels.Pixel
import com.sksamuel.scrimage.nio.PngWriter
import Chroma_Subsampling_Image_Compressor.{SpatialDownsampler, PixelYCbCrBundle} 
import jpeg.YCbCrUtils.ycbcr2rgb

class SpatialDownsamplerSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "SpatialDownsampler"

  // Unit tests for SpatialDownsampler itself (these should be fine as they don't depend on ImageProcessorParams)
  it should "downsample a 4×4 YCbCr image by factor 2" in {
    test(new SpatialDownsampler(4, 4, 2)) { dut => 
      dut.io.out.ready.poke(true.B)
      var inIdx = 0
      var outCount = 0
      val totalIn = 16
      val expected = Seq(0, 2, 8, 10)

      while (outCount < expected.size) {
        dut.io.in.valid.poke(inIdx < totalIn && dut.io.in.ready.peek().litToBoolean)
        if (dut.io.in.valid.peek().litToBoolean) {
          dut.io.in.bits.y.poke(inIdx.U)
          dut.io.in.bits.cb.poke((100 + inIdx).U)
          dut.io.in.bits.cr.poke((200 + inIdx).U)
          inIdx += 1
        }
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
      dut.io.out.ready.poke(false.B)
      dut.clock.step() 
      dut.io.in.ready.peek().litToBoolean shouldBe false

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

  behavior of "ImageProcessor Integration with SpatialDownsampler" 
  
  it should "read a 16×16 PNG, process with 4:2:0 chroma + 2×2 spatial downsample, and write an 8×8 color PNG" in {
    val input = ImageProcessorModel.readImage("./test_images/in16x16.png") 
    val w = input.width
    val h = input.height

    val pixelBuffer = scala.collection.mutable.ArrayBuffer[(Int, Int, Int)]()
    for (r_idx <- 0 until h; c_idx <- 0 until w) {
      val px = input.pixel(c_idx, r_idx)
      pixelBuffer.append((px.red(), px.green(), px.blue()))
    }
    val simplePixels = pixelBuffer.toArray

    val spatialFactor = 2
    val outW    = w / spatialFactor
    val outH    = h / spatialFactor
    val totalO  = outW * outH
    
    val params = ImageProcessorParams( 
      width        = w,
      height       = h,
      factor       = spatialFactor,
      chromaParamA = 2, // For 4:2:0
      chromaParamB = 0  // For 4:2:0
    )

    test(new ImageProcessor(params)) { dut => 
      dut.io.out.ready.poke(true.B)
      dut.io.sof.poke(true.B)   
      dut.io.eol.poke(false.B)  

      var inIdx  = 0
      var outIdx = 0
      val buf = new java.awt.image.BufferedImage(outW, outH, java.awt.image.BufferedImage.TYPE_INT_RGB)

      var timeoutCycles = (w * h * 5) + 2000

      while (outIdx < totalO && timeoutCycles > 0) {
        val canPush = inIdx < simplePixels.length && dut.io.in.ready.peek().litToBoolean
        dut.io.in.valid.poke(canPush)
        if (canPush) {
          val (r_val, g_val, b_val) = simplePixels(inIdx)
          dut.io.in.bits.r.poke(r_val.U)
          dut.io.in.bits.g.poke(g_val.U)
          dut.io.in.bits.b.poke(b_val.U)
          inIdx += 1
        }

        if (dut.io.out.valid.peek().litToBoolean) {
          val y_val  = dut.io.out.bits.y.peek().litValue.toInt
          val cb_val = dut.io.out.bits.cb.peek().litValue.toInt
          val cr_val = dut.io.out.bits.cr.peek().litValue.toInt
          val (r_conv, g_conv, b_conv) = YCbCrUtils.ycbcr2rgb(y_val, cb_val, cr_val)

          val currentOutX = outIdx % outW
          val currentOutY = outIdx / outH
          
          if (currentOutX < outW && currentOutY < outH) {
            val colorForAwt = new java.awt.Color(r_conv, g_conv, b_conv)
            buf.setRGB(currentOutX, currentOutY, colorForAwt.getRGB())
          }
          outIdx += 1
        }
        dut.clock.step()
        timeoutCycles -=1
      }
      
      if (timeoutCycles <= 0 && outIdx < totalO) {
        fail(s"Test timed out waiting for output. Collected $outIdx out of $totalO pixels.")
      }
      outIdx shouldBe totalO 

      val img: ImmutableImage = ImmutableImage.wrapAwt(buf)
      ImageProcessorModel.writeImage(img.copy(), "./APP_OUTPUT/spatial_downsampler_integration_420_sf2.png")
      println("Wrote APP_OUTPUT/spatial_downsampler_integration_420_sf2.png")
    }
  }
}
