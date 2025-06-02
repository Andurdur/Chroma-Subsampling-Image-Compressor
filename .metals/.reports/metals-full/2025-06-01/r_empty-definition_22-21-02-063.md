error id: file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/test/scala/jpeg/ColorQuantizerImageSpec.scala:`<none>`.
file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/test/scala/jpeg/ColorQuantizerImageSpec.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -chisel3/image/setColor.
	 -chisel3/image/setColor#
	 -chisel3/image/setColor().
	 -chisel3/util/image/setColor.
	 -chisel3/util/image/setColor#
	 -chisel3/util/image/setColor().
	 -chiseltest/image/setColor.
	 -chiseltest/image/setColor#
	 -chiseltest/image/setColor().
	 -image/setColor.
	 -image/setColor#
	 -image/setColor().
	 -scala/Predef.image.setColor.
	 -scala/Predef.image.setColor#
	 -scala/Predef.image.setColor().
offset: 2489
uri: file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/test/scala/jpeg/ColorQuantizerImageSpec.scala
text:
```scala
package Chroma_Subsampling_Image_Compressor

import chisel3._
import chisel3.util._ // For DecoupledIO
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.MutableImage
import com.sksamuel.scrimage.nio.PngWriter
import com.sksamuel.scrimage.color.RGBColor // For creating colors for Scrimage

import java.io.File
import java.awt.image.BufferedImage
import scala.collection.mutable.ListBuffer

// Assuming YCbCrUtils object (with ycbcr2rgb) is in this package or imported
import Chroma_Subsampling_Image_Compressor.YCbCrUtils.ycbcr2rgb

// Assuming ColorQuantizer.scala (with QuantizationMode enum) and
// PixelYCbCrBundle (with y, cb, cr fields) are in this package or imported.

class ColorQuantizerImageSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  behavior of "ColorQuantizer with Image Files"

  val originalBitWidth = 8 // Consistent with ColorQuantizer and PixelYCbCrBundle

  // Software model of the RGB to YCbCr conversion, mimicking the
  // fixed-point arithmetic of the user's RGB2YCbCr Chisel module.
  def rgbToYCbCr_fixedPointModel(r_in: Int, g_in: Int, b_in: Int): (Int, Int, Int) = {
    val R = math.max(0, math.min(255, r_in))
    val G = math.max(0, math.min(255, g_in))
    val B = math.max(0, math.min(255, b_in))

    val yInt  =  77 * R + 150 * G +  29 * B
    val cbInt = -43 * R -  85 * G + 128 * B
    val crInt = 128 * R - 107 * G -  21 * B
    
    def clampUInt8(value: Int): Int = {
      if (value < 0) 0
      else if (value > 255) 255
      else value
    }
    
    val y_final  = clampUInt8((yInt  + 128) / 256)
    val cb_final = clampUInt8(((cbInt + 128) / 256) + 128)
    val cr_final = clampUInt8(((crInt + 128) / 256) + 128)
    
    (y_final, cb_final, cr_final)
  }

  // ycbcrToRgb will use YCbCrUtils.ycbcr2rgb (imported)

  def saveRgbDataAsPng(
      filePath: String,
      rgbPixelData: Seq[(Int, Int, Int)],
      width: Int,
      height: Int
  ): Unit = {
    require(rgbPixelData.length == width * height, "Pixel data length does not match dimensions")
    val initialAwtImage: BufferedImage = ImmutableImage.filled(width, height, new RGBColor(0, 0, 0, 255).toAWT).awt()
    val image: MutableImage = new MutableImage(initialAwtImage)
    for (yIdx <- 0 until height) {
      for (xIdx <- 0 until width) {
        val (r, g, b) = rgbPixelData(yIdx * width + xIdx)
        image.setC@@olor(xIdx, yIdx, new RGBColor(r, g, b, 255))
      }
    }
    val file = new File(filePath)
    Option(file.getParentFile).foreach(_.mkdirs())
    image.output(PngWriter.MaxCompression, file) 
    println(s"Output image saved to: $filePath")
  }

  val inputImagePath = "./test_images/in128x128.png" // Using 128x128 image
  val outputDir = "./output_images_quantizer"       // Changed output directory name
  val testBitWidth = 8 // Should match originalBitWidth in ColorQuantizer & PixelYCbCrBundle

  // Quantization modes to test (ensure QuantizationMode enum is accessible)
  val modesToTest = Seq(
    ("Q24bit", QuantizationMode.Q_24BIT),
    ("Q16bit", QuantizationMode.Q_16BIT),
    ("Q8bit",  QuantizationMode.Q_8BIT)
  )

  modesToTest.foreach { case (modeNameSuffix, quantModeEnum) =>
    it should s"process '$inputImagePath' with Quantization Mode $quantModeEnum ($modeNameSuffix) and save output" in {
      
      println(s"Starting test for QuantizationMode: $quantModeEnum ($modeNameSuffix) with image: $inputImagePath")
      val inputImageFile = new File(inputImagePath)
      if (!inputImageFile.exists()) {
        fail(s"Input image not found: $inputImagePath")
      }
      val inputImage = ImmutableImage.loader().fromFile(inputImageFile)
      val imageWidth = inputImage.width
      val imageHeight = inputImage.height
      println(s"Read input image: $inputImagePath (${imageWidth}x$imageHeight)")

      // 1. Convert input RGB image to YCbCr using the fixed-point software model
      val ycbcrInputToDUT = ListBuffer[(Int, Int, Int)]()
      for (yIdx <- 0 until imageHeight; xIdx <- 0 until imageWidth) {
        val rgb = inputImage.pixel(xIdx,yIdx)
        ycbcrInputToDUT += rgbToYCbCr_fixedPointModel(rgb.red, rgb.green, rgb.blue)
      }
      val expectedPixelCount = ycbcrInputToDUT.length
      println(s"Converted input image to YCbCr (software model) - $expectedPixelCount pixels.")

      // 2. Process with ColorQuantizer DUT
      test(new ColorQuantizer(originalBitWidth)) // Instantiate ColorQuantizer
        .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        
        dut.io.mode.poke(quantModeEnum) // Set the quantization mode for the DUT
        dut.io.in.valid.poke(false.B)
        dut.io.out.ready.poke(true.B) // Consumer is always ready
        dut.clock.step(5) // Initial settle

        val collectedYCbCrFromDUT = ListBuffer[(BigInt, BigInt, BigInt)]()
        
        val inputDriver = fork {
          println(s"DUT ($modeNameSuffix): Driving $expectedPixelCount YCbCr pixels to ColorQuantizer...")
          for (idx <- 0 until expectedPixelCount) {
            val (y_in, cb_in, cr_in) = ycbcrInputToDUT(idx)
            
            dut.io.in.valid.poke(true.B)
            // Assuming PixelYCbCrBundle has lowercase y, cb, cr fields
            dut.io.in.bits.y.poke(y_in.U(testBitWidth.W))
            dut.io.in.bits.cb.poke(cb_in.U(testBitWidth.W))
            dut.io.in.bits.cr.poke(cr_in.U(testBitWidth.W))
            
            var cyclesWaiting = 0
            val readyTimeout = 15 
            while(!dut.io.in.ready.peek().litToBoolean && cyclesWaiting < readyTimeout) {
                dut.clock.step(1)
                cyclesWaiting += 1
            }
            assert(dut.io.in.ready.peek().litToBoolean, s"DUT dataIn never became ready for pixel $idx after $cyclesWaiting cycles")
            dut.clock.step(1) // Clock for the transaction
          }
          dut.io.in.valid.poke(false.B)
          println(s"DUT ($modeNameSuffix): Finished driving pixels to ColorQuantizer.")
        }

        // Adjust timeouts for potentially larger image processing
        val cyclesPerPixelEstimate = 3 
        val baseTimeout = imageHeight + 2000 
        val collectionOverallTimeout = expectedPixelCount * cyclesPerPixelEstimate + baseTimeout
        
        println(s"DUT ($modeNameSuffix): Collecting $expectedPixelCount quantized YCbCr output pixels (timeout ${collectionOverallTimeout} cycles)...")
        var cyclesInCollection = 0
        while(collectedYCbCrFromDUT.length < expectedPixelCount && cyclesInCollection < collectionOverallTimeout) {
          if (dut.io.out.valid.peek().litToBoolean) {
            // Assuming PixelYCbCrBundle has lowercase y, cb, cr fields
            val y_out = dut.io.out.bits.y.peek().litValue
            val cb_out = dut.io.out.bits.cb.peek().litValue
            val cr_out = dut.io.out.bits.cr.peek().litValue
            collectedYCbCrFromDUT += ((y_out, cb_out, cr_out))
          }
          dut.clock.step(1)
          cyclesInCollection += 1
        }
        
        println("Initial collection loop finished.")
        inputDriver.join() // Wait for input driver to complete all its clock steps.
        println("Input driver thread confirmed complete.")

        // Final flush for any remaining pixels in the ColorQuantizer pipeline (likely 1 stage)
        val finalFlushCycles = imageHeight + 100 // Generous flush
        if (collectedYCbCrFromDUT.length < expectedPixelCount) {
            println(s"DUT ($modeNameSuffix): Performing final output collection for up to $finalFlushCycles additional cycles...")
            var cyclesInFlush = 0
            while(collectedYCbCrFromDUT.length < expectedPixelCount && cyclesInFlush < finalFlushCycles) {
                if (dut.io.out.valid.peek().litToBoolean && dut.io.out.ready.peek().litToBoolean) {
                     val y = dut.io.out.bits.y.peek().litValue
                     val cb = dut.io.out.bits.cb.peek().litValue
                     val cr = dut.io.out.bits.cr.peek().litValue
                     collectedYCbCrFromDUT += ((y, cb, cr))
                }
                dut.clock.step(1)
                cyclesInFlush += 1
            }
        }
        println(s"DUT ($modeNameSuffix): Collection complete. Collected ${collectedYCbCrFromDUT.length} pixels.")
        collectedYCbCrFromDUT.length should be (expectedPixelCount)

        // 3. Convert DUT's quantized YCbCr output back to RGB using YCbCrUtils
        val finalRgbPixels = collectedYCbCrFromDUT.map { case (y, cb, cr) =>
          YCbCrUtils.ycbcr2rgb(y.toInt, cb.toInt, cr.toInt) // Using your utility
        }
        println(s"DUT ($modeNameSuffix): Converted ${finalRgbPixels.length} output pixels back to RGB.")

        // 4. Write Output Image
        new File(outputDir).mkdirs() // Ensure output directory exists
        val outputFilename = s"$outputDir/output_quantized_${modeNameSuffix}_${imageWidth}x$imageHeight.png"
        saveRgbDataAsPng(outputFilename, finalRgbPixels.toSeq, imageWidth, imageHeight)
        
        dut.clock.step(5) // Final settle
      }
    }
  }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.