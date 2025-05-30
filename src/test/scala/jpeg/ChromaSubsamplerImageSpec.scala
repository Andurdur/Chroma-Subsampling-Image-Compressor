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
// import com.sksamuel.scrimage.Color // Removed this import, as RGBColor is used explicitly

import java.io.File
import java.awt.image.BufferedImage // For explicit AWT type if needed
import scala.collection.mutable.ListBuffer

class ChromaSubsamplerImageSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  behavior of "ChromaSubsampler with Image Files"

  def rgbToYCbCr(r: Int, g: Int, b: Int): (Int, Int, Int) = {
    val y  = Math.round(0.299 * r + 0.587 * g + 0.114 * b).toInt
    val cb = Math.round(-0.168736 * r - 0.331264 * g + 0.5 * b + 128.0).toInt
    val cr = Math.round(0.5 * r - 0.418688 * g - 0.081312 * b + 128.0).toInt
    def clamp(value: Int, min: Int, max: Int): Int = Math.max(min, Math.min(max, value))
    (clamp(y, 0, 255), clamp(cb, 0, 255), clamp(cr, 0, 255))
  }

  def ycbcrToRgb(y: Int, cb: Int, cr: Int): (Int, Int, Int) = {
    val r = Math.round(y + 1.402 * (cr - 128.0)).toInt
    val g = Math.round(y - 0.344136 * (cb - 128.0) - 0.714136 * (cr - 128.0)).toInt
    val b = Math.round(y + 1.772 * (cb - 128.0)).toInt
    def clamp(value: Int): Int = Math.max(0, Math.min(255, value))
    (clamp(r), clamp(g), clamp(b))
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
    it should s"process '$inputImagePath' with Chroma Subsampling Mode $chromaModeEnum ($modeNameSuffix) and save output" in {
      
      println(s"Starting test for mode: $chromaModeEnum ($modeNameSuffix)")
      val inputImage = ImmutableImage.loader().fromFile(inputImagePath)
      val imageWidth = inputImage.width
      val imageHeight = inputImage.height
      println(s"Read input image: $inputImagePath (${imageWidth}x$imageHeight)")

      val ycbcrInputToDUT = ListBuffer[(Int, Int, Int)]()
      for (yIdx <- 0 until imageHeight; xIdx <- 0 until imageWidth) {
        val rgb = inputImage.pixel(xIdx,yIdx)
        ycbcrInputToDUT += rgbToYCbCr(rgb.red, rgb.green, rgb.blue)
      }
      println(s"Converted input image to YCbCr (software) - ${ycbcrInputToDUT.length} pixels.")

      test(new ChromaSubsampler(imageWidth, imageHeight, testBitWidth))
        .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        
        // Set a specific clock timeout for this test if needed, e.g., dut.clock.setTimeout(0) for no timeout
        // Or increase it if 1000 is too short for the whole operation: dut.clock.setTimeout(5000)
        // Default Chiseltest timeout for activity is usually larger than the idle timeout.

        dut.io.mode.poke(chromaModeEnum)
        dut.io.dataIn.valid.poke(false.B)
        dut.io.dataOut.ready.poke(true.B)
        dut.clock.step(5) // Initial settle

        val collectedYCbCrFromDUT = ListBuffer[(BigInt, BigInt, BigInt)]()
        val expectedPixelCount = ycbcrInputToDUT.length
        
        val inputDriver = fork {
          println(s"DUT ($modeNameSuffix): Driving ${ycbcrInputToDUT.length} YCbCr pixels...")
          for (idx <- 0 until ycbcrInputToDUT.length) {
            val (y_in, cb_in, cr_in) = ycbcrInputToDUT(idx)
            
            dut.io.dataIn.valid.poke(true.B)
            dut.io.dataIn.bits.y.poke(y_in.U(testBitWidth.W))
            dut.io.dataIn.bits.cb.poke(cb_in.U(testBitWidth.W))
            dut.io.dataIn.bits.cr.poke(cr_in.U(testBitWidth.W))
            
            var cyclesWaiting = 0
            val readyTimeout = 15 // Increased slightly
            while(!dut.io.dataIn.ready.peek().litToBoolean && cyclesWaiting < readyTimeout) {
                dut.clock.step(1)
                cyclesWaiting += 1
            }
            assert(dut.io.dataIn.ready.peek().litToBoolean, s"DUT dataIn never became ready for pixel $idx after $cyclesWaiting cycles")
            dut.clock.step(1) // Clock for the transaction
          }
          dut.io.dataIn.valid.poke(false.B)
          println(s"DUT ($modeNameSuffix): Finished driving pixels.")
        }

        // Main thread collects outputs
        // This loop now uses a while condition and increments a cycle counter
        // It will stop stepping the clock once all pixels are collected or timeout is reached.
        var cyclesInCollection = 0
        val collectionOverallTimeout = expectedPixelCount * 15 + imageHeight + 100 // Generous timeout
        println(s"DUT ($modeNameSuffix): Collecting $expectedPixelCount YCbCr output pixels (timeout ${collectionOverallTimeout} cycles)...")
        
        while(collectedYCbCrFromDUT.length < expectedPixelCount && cyclesInCollection < collectionOverallTimeout) {
          if (dut.io.dataOut.valid.peek().litToBoolean) {
            val y_out = dut.io.dataOut.bits.y.peek().litValue
            val cb_out = dut.io.dataOut.bits.cb.peek().litValue
            val cr_out = dut.io.dataOut.bits.cr.peek().litValue
            collectedYCbCrFromDUT += ((y_out, cb_out, cr_out))
            // println(f"Collected pixel ${collectedYCbCrFromDUT.length}") // Optional: reduce verbosity
          }
          dut.clock.step(1)
          cyclesInCollection += 1
        }
        
        println("Initial collection loop finished.")
        inputDriver.join() // Wait for input driver to complete all its clock steps.
        println("Input driver thread confirmed complete.")

        // Final flush: After input is done, some pixels might still be in the DUT pipeline.
        // This loop only runs if needed and also has a timeout.
        var finalFlushCycleCount = 0
        val finalFlushTimeout = imageHeight + 20 // Max cycles for final flush
        if (collectedYCbCrFromDUT.length < expectedPixelCount) {
            println(s"DUT ($modeNameSuffix): Performing final output collection for up to $finalFlushTimeout additional cycles...")
            while(collectedYCbCrFromDUT.length < expectedPixelCount && finalFlushCycleCount < finalFlushTimeout) {
                if (dut.io.dataOut.valid.peek().litToBoolean && dut.io.dataOut.ready.peek().litToBoolean) {
                     val y = dut.io.dataOut.bits.y.peek().litValue
                     val cb = dut.io.dataOut.bits.cb.peek().litValue
                     val cr = dut.io.dataOut.bits.cr.peek().litValue
                     collectedYCbCrFromDUT += ((y, cb, cr))
                     // println(f"Collected pixel ${collectedYCbCrFromDUT.length} (Final flush)") // Optional
                }
                dut.clock.step(1)
                finalFlushCycleCount += 1
            }
        }
        println(s"DUT ($modeNameSuffix): Collection complete. Collected ${collectedYCbCrFromDUT.length} pixels.")
        collectedYCbCrFromDUT.length should be (expectedPixelCount)

        val finalRgbPixels = collectedYCbCrFromDUT.map { case (y, cb, cr) =>
          ycbcrToRgb(y.toInt, cb.toInt, cr.toInt)
        }
        println(s"DUT ($modeNameSuffix): Converted ${finalRgbPixels.length} output pixels back to RGB.")

        new File(outputDir).mkdirs()
        val outputFilename = s"$outputDir/output_chroma_${modeNameSuffix}_${imageWidth}x$imageHeight.png"
        saveRgbDataAsPng(outputFilename, finalRgbPixels.toSeq, imageWidth, imageHeight)
        
        // dut.clock.step(5) // Final settle, may not be necessary if loops above manage clocking until done.
      }
    }
  }
}

