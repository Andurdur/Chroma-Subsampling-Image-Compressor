package jpeg // This file is in the jpeg package

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
import scala.collection.mutable.{ListBuffer, ArrayBuffer} 

import Chroma_Subsampling_Image_Compressor.{ChromaSubsampler, PixelYCbCrBundle}
import jpeg.YCbCrUtils.ycbcr2rgb


class ChromaSubsamplerImageSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  behavior of "ChromaSubsampler with Image Files (Fully Parameterized J:a:b in jpeg package)"

  val originalBitWidth = 8

  def rgbToYCbCr_fixedPointModel(r_in: Int, g_in: Int, b_in: Int): (Int, Int, Int) = {
    val R = math.max(0, math.min(255, r_in))
    val G = math.max(0, math.min(255, g_in))
    val B = math.max(0, math.min(255, b_in))
    val yInt  =  77 * R + 150 * G +  29 * B
    val cbInt = -43 * R -  85 * G + 128 * B
    val crInt = 128 * R - 107 * G -  21 * B
    def clampUInt8(value: Int): Int = {
      if (value < 0) 0 else if (value > 255) 255 else value
    }
    val y_final  = clampUInt8((yInt  + 128) / 256)
    val cb_final = clampUInt8(((cbInt + 128) / 256) + 128)
    val cr_final = clampUInt8(((crInt + 128) / 256) + 128)
    (y_final, cb_final, cr_final)
  }

  // Software model for chroma subsampling based on J:a:b parameters
  def subsampleChromaSw(
      ycbcrData: Seq[(Int, Int, Int)],
      width: Int, height: Int,
      param_a: Int, param_b: Int
  ): Seq[(Int, Int, Int)] = {
    require(Seq(4,2,1).contains(param_a), "param_a for SW model must be 4, 2, or 1")
    require(param_b == param_a || param_b == 0, "param_b for SW model must be param_a or 0")

    val hFactor = 4 / param_a
    val vFactor = if (param_b == 0 && param_a != 0) 2 else 1
    val output = ArrayBuffer[(Int, Int, Int)]()
    var lastCb = 0 
    var lastCr = 0

    for (r <- 0 until height) {
      for (c <- 0 until width) {
        val (y, cb, cr) = ycbcrData(r * width + c)
        var currentCbToOutput = lastCb
        var currentCrToOutput = lastCr

        val sampleHorizontally = (c % hFactor) == 0
        val sampleVertically = (r % vFactor) == 0

        if (sampleHorizontally && sampleVertically) {
          currentCbToOutput = cb
          currentCrToOutput = cr
          lastCb = cb 
          lastCr = cr
        }
        output.append((y, currentCbToOutput, currentCrToOutput))
      }
    }
    output.toSeq
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

  val inputImagePath = "./test_images/in16x16.png" 
  val outputDir = "./APP_OUTPUT/chroma_subsampler_parameterized_tests"
  
  val chromaTestCases = Seq(
    ("444", 4, 4), 
    ("422", 2, 2), 
    ("420", 2, 0), 
    ("411", 1, 1)  
  )

  chromaTestCases.foreach { case (testNameSuffix, paramA, paramB) =>
    it should s"process an image with Chroma Subsampling 4:$paramA:$paramB ($testNameSuffix) and save output" in {
      
      println(s"Starting test for chroma 4:$paramA:$paramB ($testNameSuffix) with image: $inputImagePath")
      val inputImageFile = new File(inputImagePath)
      if (!inputImageFile.exists()) {
        fail(s"Input image not found: $inputImagePath")
      }
      val inputImage = ImmutableImage.loader().fromFile(inputImageFile)
      val imageWidth = inputImage.width
      val imageHeight = inputImage.height
      println(s"Read input image: $inputImagePath (${imageWidth}x$imageHeight)")

      val ycbcrInputToDUT_list = ListBuffer[(Int, Int, Int)]()
      for (yIdx <- 0 until imageHeight; xIdx <- 0 until imageWidth) {
        val rgb = inputImage.pixel(xIdx,yIdx)
        ycbcrInputToDUT_list += rgbToYCbCr_fixedPointModel(rgb.red, rgb.green, rgb.blue)
      }
      val ycbcrInputToDUT = ycbcrInputToDUT_list.toSeq
      val expectedPixelCount = ycbcrInputToDUT.length
      println(s"Converted input image to YCbCr (software model) - $expectedPixelCount pixels.")

      test(new ChromaSubsampler(
                imageWidth = imageWidth, 
                imageHeight = imageHeight, 
                bitWidth = originalBitWidth,
                param_a = paramA,
                param_b = paramB
                ))
        .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        
        dut.io.dataIn.valid.poke(false.B)
        dut.io.dataOut.ready.poke(true.B)
        dut.clock.step(5)

        val collectedYCbCrFromDUT = ListBuffer[(Int, Int, Int)]()
        
        val inputDriver = fork {
          println(s"DUT ($testNameSuffix): Driving $expectedPixelCount YCbCr pixels...")
          for (idx <- 0 until expectedPixelCount) {
            val (y_in, cb_in, cr_in) = ycbcrInputToDUT(idx)
            
            dut.io.dataIn.valid.poke(true.B)
            dut.io.dataIn.bits.y.poke(y_in.U(originalBitWidth.W))
            dut.io.dataIn.bits.cb.poke(cb_in.U(originalBitWidth.W))
            dut.io.dataIn.bits.cr.poke(cr_in.U(originalBitWidth.W))
            
            var cyclesWaiting = 0
            val readyTimeout = 30 
            while(!dut.io.dataIn.ready.peek().litToBoolean && cyclesWaiting < readyTimeout) {
                dut.clock.step(1)
                cyclesWaiting += 1
            }
            assert(dut.io.dataIn.ready.peek().litToBoolean, s"DUT dataIn never became ready for pixel $idx after $cyclesWaiting cycles")
            dut.clock.step(1)
          }
          dut.io.dataIn.valid.poke(false.B)
          println(s"DUT ($testNameSuffix): Finished driving pixels.")
        }

        val cyclesPerPixelEstimate = 5 
        val baseTimeout = imageHeight + 4000 
        val collectionOverallTimeout = expectedPixelCount * cyclesPerPixelEstimate + baseTimeout
        
        println(s"DUT ($testNameSuffix): Collecting $expectedPixelCount YCbCr output pixels (timeout ${collectionOverallTimeout} cycles)...")
        var cyclesInCollection = 0
        while(collectedYCbCrFromDUT.length < expectedPixelCount && cyclesInCollection < collectionOverallTimeout) {
          if (dut.io.dataOut.valid.peek().litToBoolean) {
            val y_out = dut.io.dataOut.bits.y.peek().litValue.toInt
            val cb_out = dut.io.dataOut.bits.cb.peek().litValue.toInt
            val cr_out = dut.io.dataOut.bits.cr.peek().litValue.toInt
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
                if (dut.io.dataOut.valid.peek().litToBoolean && dut.io.dataOut.ready.peek().litToBoolean) {
                     val y = dut.io.dataOut.bits.y.peek().litValue.toInt
                     val cb = dut.io.dataOut.bits.cb.peek().litValue.toInt
                     val cr = dut.io.dataOut.bits.cr.peek().litValue.toInt
                     collectedYCbCrFromDUT += ((y, cb, cr))
                }
                dut.clock.step(1)
                cyclesInFlush += 1
            }
        }
        println(s"DUT ($testNameSuffix): Collection complete. Collected ${collectedYCbCrFromDUT.length} pixels.")
        collectedYCbCrFromDUT.length should be (expectedPixelCount)

        val swSubsampledYCbCr = subsampleChromaSw(ycbcrInputToDUT, imageWidth, imageHeight, paramA, paramB)
        
        collectedYCbCrFromDUT.length should be (swSubsampledYCbCr.length)
        for(i <- 0 until expectedPixelCount) {
            withClue(s"Pixel $i, Y component:") { collectedYCbCrFromDUT(i)._1 shouldBe swSubsampledYCbCr(i)._1 }
            withClue(s"Pixel $i, Cb component:") { collectedYCbCrFromDUT(i)._2 shouldBe swSubsampledYCbCr(i)._2 }
            withClue(s"Pixel $i, Cr component:") { collectedYCbCrFromDUT(i)._3 shouldBe swSubsampledYCbCr(i)._3 }
        }
        println(s"DUT ($testNameSuffix): Verified DUT output against software chroma subsampling model.")


        val finalRgbPixels = collectedYCbCrFromDUT.map { case (y, cb, cr) =>
          YCbCrUtils.ycbcr2rgb(y, cb, cr)
        }
        println(s"DUT ($testNameSuffix): Converted ${finalRgbPixels.length} output pixels back to RGB.")

        new File(outputDir).mkdirs()
        val outputFilename = s"$outputDir/output_chroma_4-${paramA}-${paramB}_${testNameSuffix}_${imageWidth}x$imageHeight.png"
        saveRgbDataAsPng(outputFilename, finalRgbPixels.toSeq, imageWidth, imageHeight)
        
        dut.clock.step(5)
      }
    }
  }
}
