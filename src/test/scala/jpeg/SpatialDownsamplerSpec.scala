package Chroma_Subsampling_Image_Compressor // Test file is in this package

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.MutableImage
import com.sksamuel.scrimage.pixels.Pixel

// Import necessary classes from the 'jpeg' package
import jpeg.ImageProcessorParams
import jpeg.ImageProcessor
import jpeg.YCbCrUtils.ycbcr2rgb // Utility from jpeg.YCbCrUtils
// PixelBundle and PixelYCbCrBundle are defined in the current Chroma_Subsampling_Image_Compressor package
// (in PixelBundle.scala), so they should not be imported from 'jpeg'.
// import jpeg.PixelBundle          // REMOVED - Incorrect import
// import jpeg.PixelYCbCrBundle     // REMOVED - Incorrect import
import jpeg.ImageProcessorModel  // Import the ImageProcessorModel object

// SpatialDownsampler (class) and ChromaSubsamplingMode (object/enum) are assumed to be defined
// within the current 'Chroma_Subsampling_Image_Compressor' package.

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

  behavior of "ImageProcessorModel Integration"
  it should "read/write image file via ImageProcessorModel" in {
    val image = ImageProcessorModel.readImage("./test_images/in16x16.png")
    ImageProcessorModel.writeImage(image.copy(), "./output_images/out16x16_model_copy.png")
  }
  it should "read a 16×16 PNG, process 4:2:0 + 2×2 downsample, and write an 8×8 color PNG" in {
    val input: ImmutableImage = ImageProcessorModel.readImage("./test_images/in16x16.png")
    val w: Int = input.width
    val h: Int = input.height

    val pixelBuffer = scala.collection.mutable.ArrayBuffer[(Int, Int, Int)]()
    for (r_idx <- 0 until h; c_idx <- 0 until w) {
      val px: Pixel = input.pixel(c_idx, r_idx)
      pixelBuffer.append((px.red(), px.green(), px.blue()))
    }
    val simplePixels: Array[(Int, Int, Int)] = pixelBuffer.toArray

    val outW: Int = w / 2
    val outH: Int = h / 2
    val totalO: Int = outW * outH
    
    val params: ImageProcessorParams = ImageProcessorParams(
      width     = w,
      height    = h,
      factor    = 2,
      chromaMode = ChromaSubsamplingMode.CHROMA_420
    )

    test(new ImageProcessor(params)) { dut =>
      dut.io.out.ready.poke(true.B)
      dut.io.sof.poke(true.B)
      dut.io.eol.poke(false.B)

      var inIdx: Int  = 0
      var outIdx: Int = 0
      val buf = new java.awt.image.BufferedImage(outW, outH, java.awt.image.BufferedImage.TYPE_INT_RGB)

      while (outIdx < totalO) {
        val canPush: Boolean = inIdx < simplePixels.length && dut.io.in.ready.peek().litToBoolean
        dut.io.in.valid.poke(canPush)
        if (canPush) {
          val rgbTuple: (Int, Int, Int) = simplePixels(inIdx)
          val r_val: Int = rgbTuple._1
          val g_val: Int = rgbTuple._2
          val b_val: Int = rgbTuple._3
          dut.io.in.bits.r.poke(r_val.U)
          dut.io.in.bits.g.poke(g_val.U)
          dut.io.in.bits.b.poke(b_val.U)
          inIdx += 1
        }

        if (dut.io.out.valid.peek().litToBoolean) {
          val y_val: Int  = dut.io.out.bits.y.peek().litValue.toInt
          val cb_val: Int = dut.io.out.bits.cb.peek().litValue.toInt
          val cr_val: Int = dut.io.out.bits.cr.peek().litValue.toInt
          val (r_conv, g_conv, b_conv): (Int, Int, Int) = ycbcr2rgb(y_val, cb_val, cr_val)

          val currentOutX: Int = outIdx % outW
          val currentOutY: Int = outIdx / outH
          
          if (currentOutX < outW && currentOutY < outH) {
            val colorForAwt = new java.awt.Color(r_conv, g_conv, b_conv)
            buf.setRGB(currentOutX, currentOutY, colorForAwt.getRGB())
          }
          outIdx += 1
        }
        if(inIdx == simplePixels.length && !dut.io.in.valid.peek().litToBoolean && outIdx < totalO && !dut.io.out.valid.peek().litToBoolean) {
          // Optional: Advance clock if stuck waiting for output
        }
        dut.clock.step()
      }
      
      var finalFlushCycles: Int = 0
      val maxFlushCycles: Int = w * h 
      while(outIdx < totalO && finalFlushCycles < maxFlushCycles) {
          if (dut.io.out.valid.peek().litToBoolean && dut.io.out.ready.peek().litToBoolean) { 
            val y_val: Int  = dut.io.out.bits.y.peek().litValue.toInt
            val cb_val: Int = dut.io.out.bits.cb.peek().litValue.toInt
            val cr_val: Int = dut.io.out.bits.cr.peek().litValue.toInt
            val (r_conv, g_conv, b_conv): (Int, Int, Int) = ycbcr2rgb(y_val, cb_val, cr_val)

            val currentOutX: Int = outIdx % outW
            val currentOutY: Int = outIdx / outH
            
            if (currentOutX < outW && currentOutY < outH) {
                val colorForAwt = new java.awt.Color(r_conv, g_conv, b_conv)
                buf.setRGB(currentOutX, currentOutY, colorForAwt.getRGB())
            }
            outIdx += 1
          }
          dut.clock.step()
          finalFlushCycles +=1
      }
      outIdx shouldBe totalO 

      val img: ImmutableImage = ImmutableImage.wrapAwt(buf)
      ImageProcessorModel.writeImage(img.copy(), "./output_images/out16x16_processed.png")
      println("Wrote out16x16_processed.png — check that the 2×2‐averaged color blocks match the 16×16 input.")
    }
  }
}
