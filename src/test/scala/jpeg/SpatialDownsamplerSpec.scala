package Chroma_Subsampling_Image_Compressor

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.awt.Color

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.MutableImage
import com.sksamuel.scrimage.pixels.Pixel
import com.sksamuel.scrimage.nio.PngWriter
import com.sksamuel.scrimage.color.RGBColor

import Chroma_Subsampling_Image_Compressor.ReferenceModel
import Chroma_Subsampling_Image_Compressor.YCbCrUtils.ycbcr2rgb
import Chroma_Subsampling_Image_Compressor.ChromaSubsamplingMode


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

  behavior of "ImageProcessorModel"
  it should "read/write image file" in {
    val image = ImageProcessorModel.readImage("./test_images/in16x16.png")
    ImageProcessorModel.writeImage(image, "./output_images/out16x16.png")
  }
  it should "read a 16×16 PNG, process 4:2:0 + 2×2 downsample, and write an 8×8 color PNG" in {
    // 1) Read input RGB image
    val input = ImageProcessorModel.readImage("./test_images/in16x16.png")
    val w = input.width
    val h = input.height
    val totalI_spec = w * h // Renamed to avoid conflict if totalI is used later

    // Create an ArrayBuffer to store the tuples
    val pixelBuffer = scala.collection.mutable.ArrayBuffer[(Int, Int, Int)]()

    println("--- Populating pixelBuffer ---")
    for (r_idx <- 0 until h) {
      for (c_idx <- 0 until w) {
        val px = input.pixel(c_idx, r_idx) // Use c_idx, r_idx for clarity
        val redVal = px.red()
        val greenVal = px.green()
        val blueVal = px.blue()
        // Print what's being read directly
        println(s"Reading for buffer at (col=$c_idx, row=$r_idx): R=$redVal, G=$greenVal, B=$blueVal")
        pixelBuffer.append((redVal, greenVal, blueVal))
      }
    }
    val simplePixels = pixelBuffer.toArray // Convert to immutable Array
    println("--- Finished populating pixelBuffer, converted to simplePixels array ---")

    // Add an immediate check of the first few elements of simplePixels
    if (simplePixels.nonEmpty) {
      println(s"VERIFY: simplePixels(0) = ${simplePixels(0)}")
      if (simplePixels.length > 1) println(s"VERIFY: simplePixels(1) = ${simplePixels(1)}")
      if (simplePixels.length > 85) println(s"VERIFY: simplePixels(85) = ${simplePixels(85)}")
      if (simplePixels.length > 86) println(s"VERIFY: simplePixels(86) = ${simplePixels(86)}")
    }

    // 2) Prepare output buffer
    val outW    = w / 2
    val outH    = h / 2
    val totalO  = outW * outH
    val outBuf  = Array.fill[Pixel](totalO)(new Pixel(0,0,0))

    // 3) Drive the pipeline
    // pick 4:2:0 subsampling for color test
      val params = ImageProcessorParams(
        width     = w,
        height    = h,
        factor    = 2,
        chromaMode= ChromaSubsamplingMode.CHROMA_420
      )

      test(new ImageProcessor(params)) { dut =>
      // Always ready for in/out
      dut.io.out.ready.poke(true.B)
      dut.io.sof.poke(true.B)   // start-of-frame at cycle 0
      dut.io.eol.poke(false.B)  // unused in this test

      var inIdx  = 0
      var outIdx = 0
      val totalI = w * h
      val outImg = ImmutableImage.create(outW, outH)
      // get the mutable AWT image
      val buf: java.awt.image.BufferedImage = outImg.awt()

      while (outIdx < totalO) {
        // feed RGB pixels
        val canPush = inIdx < totalI && dut.io.in.ready.peek().litToBoolean
        dut.io.in.valid.poke(canPush)
        if (canPush) {
          val rgbTuple = simplePixels(inIdx)
          dut.io.in.bits.r.poke(rgbTuple._1.U)
          dut.io.in.bits.g.poke(rgbTuple._2.U)
          dut.io.in.bits.b.poke(rgbTuple._3.U)
          inIdx += 1
        }

        // collect YCbCr outputs
        if (dut.io.out.valid.peek().litToBoolean) {
          val y  = dut.io.out.bits.y.peek().litValue.toInt
          val cb = dut.io.out.bits.cb.peek().litValue.toInt
          val cr = dut.io.out.bits.cr.peek().litValue.toInt
          // reconstruct RGB and store
          val (r,g,b) = ycbcr2rgb(y, cb, cr)
          // Calculate correct 2D coordinates for the output image
          val currentOutX = outIdx % outW // Column in the output image
          val currentOutY = outIdx / outW // Row in the output image

          // Ensure these coordinates are within bounds (though they should be if outIdx < totalO)
          if (currentOutX < outW && currentOutY < outH) {
            val colorForAwt = new java.awt.Color(r, g, b) // Create java.awt.Color
            buf.setRGB(currentOutX, currentOutY, colorForAwt.getRGB()) // Use currentOutX, currentOutY
          } else {
            println(s"WARNING: outIdx $outIdx resulted in out-of-bounds coordinates ($currentOutX, $currentOutY) for output image of size ($outW x $outH)")
          }
          
          outIdx += 1
        }

        dut.clock.step()
      }
    // wrap the mutated BufferedImage back into Scrimage
    val img: ImmutableImage = ImmutableImage.wrapAwt(buf)
    // 4) Write 8×8 color PNG
    ImageProcessorModel.writeImage(img, "./output_images/out16x16.png")
    println("Wrote out8x8.png — check that the 2×2‐averaged color blocks match the 16×16 input.")
    }
  }
}
