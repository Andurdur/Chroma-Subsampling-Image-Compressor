package jpeg 

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.MutableImage
import com.sksamuel.scrimage.nio.PngWriter
import com.sksamuel.scrimage.color.RGBColor

import java.io.File
import java.awt.image.BufferedImage
import scala.collection.mutable.ListBuffer

import Chroma_Subsampling_Image_Compressor.{ColorQuantizer, PixelYCbCrBundle} 
import Chroma_Subsampling_Image_Compressor.YCbCrUtils.ycbcr2rgb

class ColorQuantizerImageSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  behavior of "ColorQuantizer with Image Files (Fully Parameterized in jpeg package)"

  val originalBitWidth = 8 // Input YCbCr components are 8-bit

  // Software model of the RGB to YCbCr conversion
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

  // Software model for quantization based on target bits
  def quantizeSw(value: Int, targetBits: Int, originalBits: Int = 8): Int = {
    if (targetBits < 1) throw new IllegalArgumentException("Target bits must be at least 1.")
    if (targetBits >= originalBits) return value // No quantization if target bits >= original
    val shiftAmount = originalBits - targetBits
    ( (value >> shiftAmount) << shiftAmount ) // Truncate LSBs
  }

  def saveRgbDataAsPng(
      filePath: String,
      rgbPixelData: Seq[(Int, Int, Int)],
      width: Int,
      height: Int
  ): Unit = {
    require(rgbPixelData.length == width * height, "Pixel data length does not match dimensions")
    val awtImage: BufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    for (yIdx <- 0 until height) {
      for (xIdx <- 0 until width) {
        val (r, g, b) = rgbPixelData(yIdx * width + xIdx)
        val color = new java.awt.Color(r,g,b).getRGB()
        awtImage.setRGB(xIdx, yIdx, color)
      }
    }
    val image = ImmutableImage.fromAwt(awtImage)
    val file = new File(filePath)
    Option(file.getParentFile).foreach(_.mkdirs())
    image.output(PngWriter.MaxCompression, file) 
    println(s"Output image saved to: $filePath")
  }

  val inputImagePath = "./test_images/in128x128.png" 
  val outputDir = "./APP_OUTPUT/quantizer_parameterized_tests"
  
  // Define test cases as tuples: (testNameSuffix, yTargetBits, cbTargetBits, crTargetBits)
  val quantizationTestCases = Seq(
    ("Y8Cb8Cr8", 8, 8, 8), // No quantization (passthrough for 8-bit original)
    ("Y6Cb5Cr5", 6, 5, 5), // Simulates a 16-bit effective YCbCr
    ("Y3Cb3Cr2", 3, 3, 2), // Simulates an 8-bit effective YCbCr
    ("Y8Cb4Cr4", 8, 4, 4), // Custom: Full Y, reduced chroma
    ("Y4Cb4Cr4", 4, 4, 4), // Custom: All components reduced
    ("Y1Cb1Cr1", 1, 1, 1)  // Custom: Minimum bits
  )

  quantizationTestCases.foreach { case (testNameSuffix, yBits, cbBits, crBits) =>
    it should s"process '$inputImagePath' with quantization Y=${yBits}b, Cb=${cbBits}b, Cr=${crBits}b ($testNameSuffix)" in {
      
      println(s"Starting test for quantization: Y=$yBits, Cb=$cbBits, Cr=$crBits ($testNameSuffix) with image: $inputImagePath")
      val inputImageFile = new File(inputImagePath)
      if (!inputImageFile.exists()) {
        fail(s"Input image not found: $inputImagePath")
      }
      val inputImage = ImmutableImage.loader().fromFile(inputImageFile)
      val imageWidth = inputImage.width
      val imageHeight = inputImage.height
      println(s"Read input image: $inputImagePath (${imageWidth}x$imageHeight)")

      val ycbcrInputToDUT = ListBuffer[(Int, Int, Int)]()
      for (yIdx <- 0 until imageHeight; xIdx <- 0 until imageWidth) {
        val rgb = inputImage.pixel(xIdx,yIdx)
        ycbcrInputToDUT += rgbToYCbCr_fixedPointModel(rgb.red, rgb.green, rgb.blue)
      }
      val expectedPixelCount = ycbcrInputToDUT.length
      println(s"Converted input image to YCbCr (software model) - $expectedPixelCount pixels.")

      // Instantiate ColorQuantizer (from Chroma_Subsampling_Image_Compressor package)
      // with the specific bit depths for this test case
      test(new ColorQuantizer(
                yTargetBits = yBits, 
                cbTargetBits = cbBits, 
                crTargetBits = crBits, 
                originalBitWidth = originalBitWidth))
        .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        
        dut.io.in.valid.poke(false.B)
        dut.io.out.ready.poke(true.B) 
        dut.clock.step(5)

        val collectedYCbCrFromDUT = ListBuffer[(Int, Int, Int)]()
        
        val inputDriver = fork {
          println(s"DUT ($testNameSuffix): Driving $expectedPixelCount YCbCr pixels to ColorQuantizer...")
          for (idx <- 0 until expectedPixelCount) {
            val (y_in, cb_in, cr_in) = ycbcrInputToDUT(idx)
            
            dut.io.in.valid.poke(true.B)
            dut.io.in.bits.y.poke(y_in.U(originalBitWidth.W))
            dut.io.in.bits.cb.poke(cb_in.U(originalBitWidth.W))
            dut.io.in.bits.cr.poke(cr_in.U(originalBitWidth.W))
            
            var cyclesWaiting = 0
            val readyTimeout = 30 
            while(!dut.io.in.ready.peek().litToBoolean && cyclesWaiting < readyTimeout) {
                dut.clock.step(1)
                cyclesWaiting += 1
            }
            assert(dut.io.in.ready.peek().litToBoolean, s"DUT dataIn never became ready for pixel $idx after $cyclesWaiting cycles")
            dut.clock.step(1) 
          }
          dut.io.in.valid.poke(false.B)
          println(s"DUT ($testNameSuffix): Finished driving pixels to ColorQuantizer.")
        }

        val cyclesPerPixelEstimate = 5 
        val baseTimeout = imageHeight + 4000 
        val collectionOverallTimeout = expectedPixelCount * cyclesPerPixelEstimate + baseTimeout
        
        println(s"DUT ($testNameSuffix): Collecting $expectedPixelCount quantized YCbCr output pixels (timeout ${collectionOverallTimeout} cycles)...")
        var cyclesInCollection = 0
        while(collectedYCbCrFromDUT.length < expectedPixelCount && cyclesInCollection < collectionOverallTimeout) {
          if (dut.io.out.valid.peek().litToBoolean) {
            val y_out = dut.io.out.bits.y.peek().litValue.toInt
            val cb_out = dut.io.out.bits.cb.peek().litValue.toInt
            val cr_out = dut.io.out.bits.cr.peek().litValue.toInt
            collectedYCbCrFromDUT += ((y_out, cb_out, cr_out))
          }
          dut.clock.step(1)
          cyclesInCollection += 1
        }
        
        println("Initial collection loop finished.")
        inputDriver.join() 
        println("Input driver thread confirmed complete.")

        val finalFlushCycles = imageHeight + 200 
        if (collectedYCbCrFromDUT.length < expectedPixelCount) {
            println(s"DUT ($testNameSuffix): Performing final output collection for up to $finalFlushCycles additional cycles...")
            var cyclesInFlush = 0
            while(collectedYCbCrFromDUT.length < expectedPixelCount && cyclesInFlush < finalFlushCycles) {
                if (dut.io.out.valid.peek().litToBoolean && dut.io.out.ready.peek().litToBoolean) {
                     val y = dut.io.out.bits.y.peek().litValue.toInt
                     val cb = dut.io.out.bits.cb.peek().litValue.toInt
                     val cr = dut.io.out.bits.cr.peek().litValue.toInt
                     collectedYCbCrFromDUT += ((y, cb, cr))
                }
                dut.clock.step(1)
                cyclesInFlush += 1
            }
        }
        println(s"DUT ($testNameSuffix): Collection complete. Collected ${collectedYCbCrFromDUT.length} pixels.")
        
        collectedYCbCrFromDUT.length should be (expectedPixelCount)

        val swQuantizedYCbCr = ycbcrInputToDUT.map { case (y_sw, cb_sw, cr_sw) =>
            (quantizeSw(y_sw, yBits), quantizeSw(cb_sw, cbBits), quantizeSw(cr_sw, crBits))
        }

        for(i <- 0 until expectedPixelCount) {
            withClue(s"Pixel $i, Y component:") { collectedYCbCrFromDUT(i)._1 shouldBe swQuantizedYCbCr(i)._1 }
            withClue(s"Pixel $i, Cb component:") { collectedYCbCrFromDUT(i)._2 shouldBe swQuantizedYCbCr(i)._2 }
            withClue(s"Pixel $i, Cr component:") { collectedYCbCrFromDUT(i)._3 shouldBe swQuantizedYCbCr(i)._3 }
        }
        println(s"DUT ($testNameSuffix): Verified DUT output against software quantization model.")

        val finalRgbPixels = collectedYCbCrFromDUT.map { case (y, cb, cr) =>
          YCbCrUtils.ycbcr2rgb(y, cb, cr)
        }
        println(s"DUT ($testNameSuffix): Converted ${finalRgbPixels.length} output pixels back to RGB.")

        new File(outputDir).mkdirs() 
        val outputFilename = s"$outputDir/output_quantized_${testNameSuffix}_${imageWidth}x$imageHeight.png"
        saveRgbDataAsPng(outputFilename, finalRgbPixels.toSeq, imageWidth, imageHeight)
        
        dut.clock.step(5)
      }
    }
  }
}
