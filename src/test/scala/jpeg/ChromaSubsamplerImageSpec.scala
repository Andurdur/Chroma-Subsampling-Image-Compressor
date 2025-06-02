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

// Using your provided YCbCrUtils for the YCbCr to RGB conversion
import Chroma_Subsampling_Image_Compressor.YCbCrUtils.ycbcr2rgb

class ChromaSubsamplerImageSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  behavior of "ChromaSubsampler with Image Files"


  def rgbToYCbCr_fixedPointModel(r_in: Int, g_in: Int, b_in: Int): (Int, Int, Int) = {
    val R = r_in
    val G = g_in
    val B = b_in


    val yInt  =  77 * R + 150 * G +  29 * B
    val cbInt = -43 * R -  85 * G + 128 * B
    val crInt = 128 * R - 107 * G -  21 * B

    def clampUInt8(value: Int): Int = {
      if (value < 0) 0
      else if (value > 255) 255
      else value
    }
    
    val y_final  = clampUInt8((yInt  + 128) / 256) // Integer division acts like right shift for positive results
    val cb_final = clampUInt8(((cbInt + 128) / 256) + 128)
    val cr_final = clampUInt8(((crInt + 128) / 256) + 128)
    
    (y_final, cb_final, cr_final)
  }

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
        image.setColor(xIdx, yIdx, new RGBColor(r, g, b, 255))
      }
    }
    val file = new File(filePath)
    Option(file.getParentFile).foreach(_.mkdirs())
    image.output(PngWriter.MaxCompression, file) 
    println(s"Output image saved to: $filePath")
  }

  val inputImagePath = "./test_images/in16x16.png" 
  val outputDir = "./output_images_chroma"
  val testBitWidth = 8 

  val modesToTest = Seq(
    ("444", ChromaSubsamplingMode.CHROMA_444),
    ("422", ChromaSubsamplingMode.CHROMA_422),
    ("420", ChromaSubsamplingMode.CHROMA_420)
  )

  modesToTest.foreach { case (modeNameSuffix, chromaModeEnum) =>
    it should s"process an image with Chroma Subsampling Mode $chromaModeEnum ($modeNameSuffix) and save output" in {
      
      println(s"Starting test for mode: $chromaModeEnum ($modeNameSuffix) with image: $inputImagePath")
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
      println(s"Converted input image to YCbCr (software model matching DUT) - $expectedPixelCount pixels.")

      test(new ChromaSubsampler(imageWidth, imageHeight, testBitWidth))
        .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        
        dut.io.mode.poke(chromaModeEnum)
        dut.io.dataIn.valid.poke(false.B)
        dut.io.dataOut.ready.poke(true.B)
        dut.clock.step(5)

        val collectedYCbCrFromDUT = ListBuffer[(BigInt, BigInt, BigInt)]()
        
        val inputDriver = fork {
          println(s"DUT ($modeNameSuffix): Driving $expectedPixelCount YCbCr pixels...")
          for (idx <- 0 until expectedPixelCount) {
            val (y_in, cb_in, cr_in) = ycbcrInputToDUT(idx)
            
            dut.io.dataIn.valid.poke(true.B)
            dut.io.dataIn.bits.y.poke(y_in.U(testBitWidth.W))
            dut.io.dataIn.bits.cb.poke(cb_in.U(testBitWidth.W))
            dut.io.dataIn.bits.cr.poke(cr_in.U(testBitWidth.W))
            
            var cyclesWaiting = 0
            val readyTimeout = 15 
            while(!dut.io.dataIn.ready.peek().litToBoolean && cyclesWaiting < readyTimeout) {
                dut.clock.step(1)
                cyclesWaiting += 1
            }
            assert(dut.io.dataIn.ready.peek().litToBoolean, s"DUT dataIn never became ready for pixel $idx after $cyclesWaiting cycles")
            dut.clock.step(1)
          }
          dut.io.dataIn.valid.poke(false.B)
          println(s"DUT ($modeNameSuffix): Finished driving pixels.")
        }

        val cyclesPerPixelEstimate = 3 
        val baseTimeout = imageHeight + 2000 
        val collectionOverallTimeout = expectedPixelCount * cyclesPerPixelEstimate + baseTimeout
        
        println(s"DUT ($modeNameSuffix): Collecting $expectedPixelCount YCbCr output pixels (timeout ${collectionOverallTimeout} cycles)...")
        var cyclesInCollection = 0
        while(collectedYCbCrFromDUT.length < expectedPixelCount && cyclesInCollection < collectionOverallTimeout) {
          if (dut.io.dataOut.valid.peek().litToBoolean) {
            val y_out = dut.io.dataOut.bits.y.peek().litValue
            val cb_out = dut.io.dataOut.bits.cb.peek().litValue
            val cr_out = dut.io.dataOut.bits.cr.peek().litValue
            collectedYCbCrFromDUT += ((y_out, cb_out, cr_out))
          }
          dut.clock.step(1)
          cyclesInCollection += 1
        }
        
        println("Initial collection loop finished.")
        inputDriver.join()
        println("Input driver thread confirmed complete.")

        val finalFlushCycles = imageHeight + 100 
        if (collectedYCbCrFromDUT.length < expectedPixelCount) {
            println(s"DUT ($modeNameSuffix): Performing final output collection for up to $finalFlushCycles additional cycles...")
            var cyclesInFlush = 0
            while(collectedYCbCrFromDUT.length < expectedPixelCount && cyclesInFlush < finalFlushCycles) {
                if (dut.io.dataOut.valid.peek().litToBoolean && dut.io.dataOut.ready.peek().litToBoolean) {
                     val y = dut.io.dataOut.bits.y.peek().litValue
                     val cb = dut.io.dataOut.bits.cb.peek().litValue
                     val cr = dut.io.dataOut.bits.cr.peek().litValue
                     collectedYCbCrFromDUT += ((y, cb, cr))
                }
                dut.clock.step(1)
                cyclesInFlush += 1
            }
        }
        println(s"DUT ($modeNameSuffix): Collection complete. Collected ${collectedYCbCrFromDUT.length} pixels.")
        collectedYCbCrFromDUT.length should be (expectedPixelCount)

        val finalRgbPixels = collectedYCbCrFromDUT.map { case (y, cb, cr) =>

          YCbCrUtils.ycbcr2rgb(y.toInt, cb.toInt, cr.toInt)
        }
        println(s"DUT ($modeNameSuffix): Converted ${finalRgbPixels.length} output pixels back to RGB.")

        new File(outputDir).mkdirs()
        val outputFilename = s"$outputDir/output_chroma_${modeNameSuffix}_${imageWidth}x$imageHeight.png"
        saveRgbDataAsPng(outputFilename, finalRgbPixels.toSeq, imageWidth, imageHeight)
        
        dut.clock.step(5)
      }
    }
  }
}
