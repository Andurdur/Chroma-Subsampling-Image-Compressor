package jpeg

import chisel3._
import chiseltest._
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.MutableImage
import com.sksamuel.scrimage.color.RGBColor 
import java.io.File
import scala.collection.mutable.ArrayBuffer
import java.awt.{Color => AwtColor} // Using java.awt.Color for ImmutableImage.filled

// Imports from your project structure
import Chroma_Subsampling_Image_Compressor.{PixelBundle, PixelYCbCrBundle, ChromaSubsamplingMode, QuantizationMode}


/**
 * Main application to process an image using the ImageCompressorTop Chisel module.
 */
object ImageCompressionApp extends App {

  /**
   * Processes an image through the ImageCompressorTop DUT.
   */
  def processImage(
      inputImagePath: String,
      outputImagePath: String,
      chromaModeToUse: ChromaSubsamplingMode.Type, // Parameter for Chroma mode
      quantModeToUse: QuantizationMode.Type,     // Parameter for Quantization mode
      spatialFactorToUse: Int                  // Parameter for Spatial downsampling factor
  ): Unit = {

    println(s"Reading image from: $inputImagePath")
    val inputImage: ImmutableImage = ImageProcessorModel.readImage(inputImagePath)
    val imageWidth = inputImage.width
    val imageHeight = inputImage.height

    println(s"Input image dimensions: ${imageWidth}x$imageHeight")

    if (imageWidth % spatialFactorToUse != 0 || imageHeight % spatialFactorToUse != 0) {
      println(s"[ERROR] Image dimensions (${imageWidth}x$imageHeight) must be divisible by spatialFactor ($spatialFactorToUse).")
      return
    }

    val outputWidth = imageWidth / spatialFactorToUse
    val outputHeight = imageHeight / spatialFactorToUse
    println(s"Output image dimensions will be: ${outputWidth}x$outputHeight")
    println(s"Processing with ChromaMode: $chromaModeToUse, QuantMode: $quantModeToUse, SpatialFactor: $spatialFactorToUse")

    val outputPixelData = ArrayBuffer[(Int, Int, Int)]()

    chiseltest.RawTester.test(
            new jpeg.ImageCompressorTop( 
                imageWidth, 
                imageHeight, 
                chromaModeToUse,  
                quantModeToUse,   
                spatialFactorToUse 
            ),
            Seq(WriteVcdAnnotation) 
        ) { dut => 
      
      // Initialize DUT IOs that are driven by the testbench BEFORE forking threads
      dut.io.out.ready.poke(true.B) // Consumer is ALWAYS ready to accept output
      dut.io.in.valid.poke(false.B)  // No valid input initially
      dut.io.sof.poke(false.B)
      dut.io.eol.poke(false.B)
      dut.clock.step(1)             // Step clock to establish initial state after pokes

      val inputDriver = fork {
        println("Input driver thread started.")
        
        for (y <- 0 until imageHeight) {
          for (x <- 0 until imageWidth) {
            // Set SOF for the very first pixel
            if (x == 0 && y == 0) {
              dut.io.sof.poke(true.B)
            }
            // Set EOL for the last pixel of a line
            if (x == imageWidth - 1) {
              dut.io.eol.poke(true.B)
            }

            val pixel = inputImage.pixel(x, y)
            dut.io.in.bits.r.poke(pixel.red().U)
            dut.io.in.bits.g.poke(pixel.green().U)
            dut.io.in.bits.b.poke(pixel.blue().U)
            dut.io.in.valid.poke(true.B)
            
            // Wait until DUT accepts the input
            while (!dut.io.in.ready.peek().litToBoolean) {
              dut.clock.step(1)
            }
            // At this point, DUT's in.ready was true, so input was (or is about to be) consumed in this cycle
            
            dut.clock.step(1) // Cycle where input is consumed

            // De-assert signals that are pulse-like or after consumption
            dut.io.in.valid.poke(false.B) 
            if (x == 0 && y == 0) {
              dut.io.sof.poke(false.B)
            }
            if (x == imageWidth - 1) {
              dut.io.eol.poke(false.B)
            }
          }
        }
        // Ensure valid is low at the very end if not already
        dut.io.in.valid.poke(false.B)
        dut.io.sof.poke(false.B)
        dut.io.eol.poke(false.B)
        println("Input driver thread finished.")
      }

      println("Output collection started.")
      // io.out.ready is kept true by the initial poke. No need to poke it in the loop.
      
      var collectedPixels = 0
      val expectedOutputPixels = outputWidth * outputHeight
      var timeoutCycles = expectedOutputPixels * 15 + 3000 
      
      while (collectedPixels < expectedOutputPixels && timeoutCycles > 0) {
        // dut.io.out.ready.poke(true.B) // REMOVED: Not needed if consumer is always ready

        if (dut.io.out.valid.peek().litToBoolean) {
          val y = dut.io.out.bits.y.peek().litValue.toInt
          val cb = dut.io.out.bits.cb.peek().litValue.toInt
          val cr = dut.io.out.bits.cr.peek().litValue.toInt

          val (r_out, g_out, b_out) = YCbCrUtils.ycbcr2rgb(y, cb, cr) 
          outputPixelData.append((r_out, g_out, b_out))
          collectedPixels += 1
        }
        dut.clock.step(1) 
        timeoutCycles -=1
      }

      if (timeoutCycles <= 0 && collectedPixels < expectedOutputPixels) {
        println(s"[WARN] Output collection timed out. Collected $collectedPixels out of $expectedOutputPixels pixels.")
      }
      println(s"Output collection finished. Collected ${outputPixelData.length} pixels.")
      
      inputDriver.join()
    }

    if (outputPixelData.length != outputWidth * outputHeight) {
      println(s"[ERROR] Mismatch in output pixel count. Expected: ${outputWidth * outputHeight}, Got: ${outputPixelData.length}")
    } else {
      println("Successfully collected all expected output pixels.")
    }

    println("Constructing output image...")
    val transparentBlackAwt = new AwtColor(0, 0, 0, 0) 
    val outputMutableImage = ImmutableImage.filled(outputWidth, outputHeight, transparentBlackAwt).copy()
    for (y <- 0 until outputHeight) {
      for (x <- 0 until outputWidth) {
        val pixelIndex = y * outputWidth + x
        if (pixelIndex < outputPixelData.length) {
          val (r, g, b) = outputPixelData(pixelIndex)
          outputMutableImage.setColor(x, y, new com.sksamuel.scrimage.color.RGBColor(r, g, b, 255))
        } else {
            outputMutableImage.setColor(x,y, new com.sksamuel.scrimage.color.RGBColor(255,0,255,255))
        }
      }
    }
    
    println(s"Writing processed image to: $outputImagePath")
    ImageProcessorModel.writeImage(outputMutableImage, outputImagePath) 
    println("Image processing complete.")
  }

  // --- Main execution ---
  // Define your input image path here
  val inputPath = "test_images/in128x128.png" // Example: ensure this image exists
  val imageName = new File(inputPath).getName.takeWhile(_ != '.')
  
  println("----------------------------------------------------")
  println("Image Compressor Application Parameters:")
  println("----------------------------------------------------")
  println(s"Input Image: $inputPath")
  println("----------------------------------------------------")
  
  // 1. CHROMA SUBSAMPLING MODE
  // Options:
  //   ChromaSubsamplingMode.CHROMA_444  (No chroma subsampling)
  //   ChromaSubsamplingMode.CHROMA_422  (Horizontal subsampling, Cb/Cr sampled at even columns)
  //   ChromaSubsamplingMode.CHROMA_420  (Horizontal & Vertical, Cb/Cr at even_col, even_row)
  val selectedChromaMode: ChromaSubsamplingMode.Type = ChromaSubsamplingMode.CHROMA_420
  println(s"Selected Chroma Subsampling Mode: $selectedChromaMode")

  // 2. COLOR QUANTIZATION MODE
  // Options:
  //   QuantizationMode.Q_24BIT  (8-bits for Y, 8-bits for Cb, 8-bits for Cr - No quantization)
  //   QuantizationMode.Q_16BIT  (Effective bits: Y=6, Cb=5, Cr=5)
  //   QuantizationMode.Q_8BIT   (Effective bits: Y=3, Cb=3, Cr=2)
  val selectedQuantMode: QuantizationMode.Type = QuantizationMode.Q_8BIT
  println(s"Selected Color Quantization Mode: $selectedQuantMode")

  // 3. SPATIAL DOWNSAMPLING FACTOR
  // Options: 1, 2, 4, or 8
  // Note: Input image width and height must be divisible by this factor.
  val selectedSpatialFactor: Int = 1 // Example: no spatial downsampling
  println(s"Selected Spatial Downsampling Factor: $selectedSpatialFactor")
  println("----------------------------------------------------")


  // Output path is derived from the input name and selected parameters
  val outputBaseDirName = "APP_OUTPUT" 
  val outputFileNameSuffix = s"chroma${selectedChromaMode.toString.split('.').last}_quant${selectedQuantMode.toString.split('.').last}_sf${selectedSpatialFactor}"
  val outputPath = s"$outputBaseDirName/${imageName}_processed_${outputFileNameSuffix}.png"

  // Create output directory if it doesn't exist
  val outputDir = new File(outputBaseDirName) 
  if (!outputDir.exists()) {
    outputDir.mkdirs()
  }
  
  val inputFile = new File(inputPath)
  if (!inputFile.exists()) {
    println(s"[ERROR] Input image not found: $inputPath")
  } else {
    processImage(
      inputPath,
      outputPath,
      selectedChromaMode, 
      selectedQuantMode,  
      selectedSpatialFactor 
    )
  }
}
