error id: file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/test/scala/jpeg/ImageCompressorTopSpec.scala:`<none>`.
file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/test/scala/jpeg/ImageCompressorTopSpec.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 1344
uri: file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/test/scala/jpeg/ImageCompressorTopSpec.scala
text:
```scala
// File: src/test/scala/Chroma_Subsampling_Image_Compressor/ImageCompressorTopSpec.scala

package Chroma_Subsampling_Image_Compressor

import java.io.File

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import com.sksamuel.scrimage.{ImmutableImage, MutableImage}
import com.sksamuel.scrimage.color.RGBColor

/**
 * This Spec reads a real PNG via ImageProcessorModel.readImage(...),
 * runs it through ImageCompressorTop (with a chosen subMode/downFactor),
 * then converts the YCbCr outputs back to RGB and writes a new PNG by
 * setting each pixel in a MutableImage.
 */
class ImageCompressorTopSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  behavior of "ImageCompressorTop (with real PNG input/output)"

  it should "read a PNG, run it through color‐quant + chroma‐subsample + downsample, and write out a new PNG" in {
    // ----------------------------------------------------------------------
    // 1) Paths and parameters
    // ----------------------------------------------------------------------
    // Put "input16x16.png" under src/test/resources/
    val inputPath  = "./test_images/in16x16.png"
    val subMode    = 1       // 0 → 4:4:4, 1 → 4:2:2, 2 → 4:2:0
    val downFactor = 2       // 1, 2, 4, or @@8

    // Read the input image from disk
    val inImmutable = ImageProcessorModel.readImage(inputPath)
    val inW         = inImmutable.width
    val inH         = inImmutable.height

    // Compute output dimensions after spatial downsampling
    val outW = inW  / downFactor
    val outH = inH  / downFactor

    // ----------------------------------------------------------------------
    // 2) Extract the RGB pixels from the ImmutableImage (row‐major)
    // ----------------------------------------------------------------------
    // ImageProcessorModel.getImagePixels(...) returns Seq[Seq[Seq[Int]]]
    // pixelRows(r)(c) = Seq(rVal, gVal, bVal).
    val pixelRows: ImageProcessorModel.ImageType =
      ImageProcessorModel.getImagePixels(inImmutable)

    // Flatten into a single Seq[(Int,Int,Int)] in row‐major order:
    val allRgb: Seq[(Int,Int,Int)] = pixelRows.flatten.map { rgbSeq =>
      (rgbSeq(0), rgbSeq(1), rgbSeq(2))
    }

    assert(allRgb.length == inW * inH, s"Expected ${inW*inH} pixels but got ${allRgb.length}")

    // ----------------------------------------------------------------------
    // 3) Run the DUT under ChiselTest
    // ----------------------------------------------------------------------
    test(new ImageCompressorTop(width = inW, height = inH, subMode = subMode, downFactor = downFactor)) { c =>
      // Drive the input stream of (r,g,b), with sof/eol
      var pixelIdx = 0
      for (row <- 0 until inH; col <- 0 until inW) {
        val (rVal, gVal, bVal) = allRgb(pixelIdx)
        c.io.in.valid.poke(true.B)
        c.io.in.bits.r.poke(rVal.U)
        c.io.in.bits.g.poke(gVal.U)
        c.io.in.bits.b.poke(bVal.U)
        c.io.sof.poke((pixelIdx == 0).B)
        c.io.eol.poke((col == inW-1).B)

        // Wait until DUT is ready to accept this pixel
        while (!c.io.in.ready.peek().litToBoolean) {
          c.clock.step()
        }
        // Fire the handshake on this cycle
        c.clock.step()
        pixelIdx += 1
      }

      // Deassert valid/sof/eol so the pipeline can drain
      c.io.in.valid.poke(false.B)
      c.io.sof.poke(false.B)
      c.io.eol.poke(false.B)

      // --------------------------------------------------------------------
      // 4) Collect all YCbCr outputs from the DUT
      // --------------------------------------------------------------------
      val expectedCount  = outW * outH
      val collectedYCbCr = scala.collection.mutable.ArrayBuffer.empty[(Int,Int,Int)]

      while (collectedYCbCr.length < expectedCount) {
        // Wait until out.valid
        while (!c.io.out.valid.peek().litToBoolean) {
          c.clock.step()
        }
        // Capture the three 8-bit outputs
        val yVal  = c.io.out.bits.y.peek().litValue.toInt
        val cbVal = c.io.out.bits.cb.peek().litValue.toInt
        val crVal = c.io.out.bits.cr.peek().litValue.toInt

        collectedYCbCr += ((yVal, cbVal, crVal))

        // Consume it for one cycle
        c.io.out.ready.poke(true.B)
        c.clock.step()
        c.io.out.ready.poke(false.B)
      }

      collectedYCbCr.length shouldBe expectedCount

      // --------------------------------------------------------------------
      // 5) Convert each (y,cb,cr) back to RGB via YCbCrUtils.ycbcr2rgb(...)
      // --------------------------------------------------------------------
      // Call .toSeq on the ArrayBuffer so we have an immutable Seq
      val collectedRgbOut: Seq[(Int,Int,Int)] =
        collectedYCbCr.toSeq.map { case (y, cb, cr) => YCbCrUtils.ycbcr2rgb(y, cb, cr) }

      // --------------------------------------------------------------------
      // 6) Build a MutableImage, set each pixel via RGBColor, and write out
      // --------------------------------------------------------------------
      // Create a black‐filled AWT image of size (outW × outH)
      val initialAwt: java.awt.image.BufferedImage =
        ImmutableImage
          .filled(outW, outH, new RGBColor(0, 0, 0, 255).toAWT)
          .awt()

      val outImg = new MutableImage(initialAwt)

      // For each (xIdx,yIdx) in output, pick (r,g,b) from collectedRgbOut(row × outW + col)
      for (yIdx <- 0 until outH; xIdx <- 0 until outW) {
        val (r, g, b) = collectedRgbOut(yIdx * outW + xIdx)
        outImg.setColor(xIdx, yIdx, new RGBColor(r, g, b, 255))
      }

      // Output filename based on subMode/downFactor
      val outputName = s"./output_images/output_sub${subMode}_down${downFactor}.png"
      ImageProcessorModel.writeImage(outImg, outputName)

      // Sanity‐check that the file now exists
      val f = new File(outputName)
      assert(f.exists(), s"Expected to find $outputName on disk")
      println(s"[INFO] → Wrote compressed image to $outputName (mode=$subMode, down=$downFactor)")
    }
  }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.