error id: file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/test/scala/jpeg/ImageCompressorTopApp.scala:`<none>`.
file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/test/scala/jpeg/ImageCompressorTopApp.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 6774
uri: file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/test/scala/jpeg/ImageCompressorTopApp.scala
text:
```scala
package jpeg

import chisel3._
import chiseltest._
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.MutableImage
import com.sksamuel.scrimage.color.RGBColor 
import java.io.File
import scala.collection.mutable.ArrayBuffer
import java.awt.{Color => AwtColor}
import Chroma_Subsampling_Image_Compressor.{PixelBundle, PixelYCbCrBundle}


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
      // Specific operation configs
      chromaParamA: Int, // J:a:b 'a' parameter
      chromaParamB: Int, // J:a:b 'b' parameter
      yTargetBits: Int,
      cbTargetBits: Int,
      crTargetBits: Int,
      spatialFactorToUse: Int,
      // Pipeline order configuration
      op1: ProcessingStep.Type,
      op2: ProcessingStep.Type,
      op3: ProcessingStep.Type
  ): Unit = {

    println(s"Reading image from: $inputImagePath")
    val inputImage: ImmutableImage = ImageProcessorModel.readImage(inputImagePath)
    val imageWidth = inputImage.width
    val imageHeight = inputImage.height

    println(s"Input image dimensions: ${imageWidth}x$imageHeight")

    val finalOutputWidth = if (op1 == ProcessingStep.SpatialSampling || op2 == ProcessingStep.SpatialSampling || op3 == ProcessingStep.SpatialSampling) imageWidth / spatialFactorToUse else imageWidth
    val finalOutputHeight = if (op1 == ProcessingStep.SpatialSampling || op2 == ProcessingStep.SpatialSampling || op3 == ProcessingStep.SpatialSampling) imageHeight / spatialFactorToUse else imageHeight
    
    if (imageWidth % spatialFactorToUse != 0 || imageHeight % spatialFactorToUse != 0) {
        if (op1 == ProcessingStep.SpatialSampling || op2 == ProcessingStep.SpatialSampling || op3 == ProcessingStep.SpatialSampling) {
             println(s"[WARN] Image dimensions (${imageWidth}x$imageHeight) are not perfectly divisible by spatialFactor ($spatialFactorToUse). SpatialDownsampler might truncate.")
        }
    }

    println(s"Expected output image dimensions will be: ${finalOutputWidth}x$finalOutputHeight")
    println(s"Processing with pipeline order: $op1 -> $op2 -> $op3")
    println(s"  Chroma Subsampling (J:a:b format, J=4): 4:$chromaParamA:$chromaParamB")
    println(s"  QuantBits (Y/Cb/Cr): $yTargetBits/$cbTargetBits/$crTargetBits, SpatialFactor: $spatialFactorToUse")

    val outputPixelData = ArrayBuffer[(Int, Int, Int)]()

    chiseltest.RawTester.test(
            new jpeg.ImageCompressorTop( 
                imageWidth, 
                imageHeight, 
                chromaParamA,    // Pass J:a:b 'a'
                chromaParamB,    // Pass J:a:b 'b'
                yTargetBits,
                cbTargetBits,
                crTargetBits,
                spatialFactorToUse,
                op1, 
                op2,
                op3
            ),
            Seq(WriteVcdAnnotation) 
        ) { dut => 
      
      dut.io.out.ready.poke(true.B) 
      dut.io.in.valid.poke(false.B)  
      dut.io.sof.poke(false.B)
      dut.io.eol.poke(false.B)
      dut.clock.step(1)             

      val inputDriver = fork {
        println("Input driver thread started.")
        
        for (y <- 0 until imageHeight) {
          for (x <- 0 until imageWidth) {
            if (x == 0 && y == 0) {
              dut.io.sof.poke(true.B)
            }
            if (x == imageWidth - 1) {
              dut.io.eol.poke(true.B)
            }

            val pixel = inputImage.pixel(x, y)
            dut.io.in.bits.r.poke(pixel.red().U)
            dut.io.in.bits.g.poke(pixel.green().U)
            dut.io.in.bits.b.poke(pixel.blue().U)
            dut.io.in.valid.poke(true.B)
            
            while (!dut.io.in.ready.peek().litToBoolean) {
              dut.clock.step(1)
            }
            
            dut.clock.step(1) 

            dut.io.in.valid.poke(false.B) 
            if (x == 0 && y == 0) dut.io.sof.poke(false.B)
            if (x == imageWidth - 1) dut.io.eol.poke(false.B)
          }
        }
        dut.io.in.valid.poke(false.B)
        dut.io.sof.poke(false.B)
        dut.io.eol.poke(false.B)
        println("Input driver thread finished.")
      }

      println("Output collection started.")
      
      var collectedPixels = 0
      val expectedOutputPixels = finalOutputWidth * finalOutputHeight
      var timeoutCycles = expectedOutputPixels * 20 + 5000 
      
      while (collectedPixels < expectedOutputPixels && timeoutCycles > 0) {
        if (dut.io.out.valid.peek().litToBoolean) {
          val y_val = dut.io.out.bits.y.peek().litValue.toInt
          val cb_val = dut.io.out.bits.cb.peek().litValue.toInt
          val cr_val = dut.io.out.bits.cr.peek().litValue.toInt

          val (r_out, g_out, b_out) = YCbCrUtils.ycbcr2rgb(y_val, cb_val, cr_val) 
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

    if (outputPixelData.length != finalOutputWidth * finalOutputHeight) {
      println(s"[ERROR] Mismatch in output pixel count. Expected: ${finalOutputWidth * finalOutputHeight}, Got: ${outputPixelData.length}")
    } else {
      println("Successfully collected all expected output pixels.")
    }

    println("Constructing output image...")
    val transparentBlackAwt = new AwtColor(0, 0, 0, 0) 
    val outputMutableImage = ImmutableImage.filled(finalOutputWidth, finalOutputHeight, transparentBlackAwt).copy()
    for (y <- 0 until finalOutputHeight) {
      for (x <- 0 until finalOutputWidth) {
        val pixelIndex = y * finalOutputWidth + x
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
    @@// println("Image processing complete.")
  }

  // --- Main execution ---
  val inputPath = "test_images/in128x128.png" 
  val imageName = new File(inputPath).getName.takeWhile(_ != '.')
  
  println("----------------------------------------------------")
  println("Image Compressor Application Parameters:")
  println("----------------------------------------------------")
  println(s"Input Image: $inputPath")
  println("----------------------------------------------------")
  
  // 1. CHROMA SUBSAMPLING PARAMETERS (J:a:b format, J=4 is fixed)
  // param_a: Horizontal sampling reference (4, 2, or 1)
  //   4 -> 4:4:x (no horizontal Cb/Cr subsampling relative to Y)
  //   2 -> 4:2:x (Cb/Cr sampled every 2nd pixel horizontally)
  //   1 -> 4:1:x (Cb/Cr sampled every 4th pixel horizontally)
  // param_b: Vertical sampling reference (must be equal to param_a or 0)
  //   param_b == param_a -> No vertical subsampling of chroma lines (e.g., 4:4:4, 4:2:2, 4:1:1)
  //   param_b == 0       -> Chroma lines subsampled by 2 (e.g., 4:4:0 (uncommon), 4:2:0, 4:1:0 (uncommon))
  val selectedChromaParamA: Int = 1 // Example: for 4:4:4
  val selectedChromaParamB: Int = 0 // Example: for 4:4:4 (param_b == param_a)
  // For 4:2:2, use: selectedChromaParamA = 2, selectedChromaParamB = 2
  // For 4:2:0, use: selectedChromaParamA = 2, selectedChromaParamB = 0
  println(s"Selected Chroma Subsampling (J:a:b): 4:$selectedChromaParamA:$selectedChromaParamB")

  // 2. COLOR QUANTIZATION TARGET BIT DEPTHS (1-8 bits each)
  val yTargetQuantBits: Int = 8 
  val cbTargetQuantBits: Int = 8 
  val crTargetQuantBits: Int = 8 
  println(s"Selected Quantization Bits (Y/Cb/Cr): $yTargetQuantBits/$cbTargetQuantBits/$crTargetQuantBits")

  // 3. SPATIAL DOWNSAMPLING FACTOR (1, 2, 4, or 8)
  val selectedSpatialFactor: Int = 1 
  println(s"Selected Spatial Downsampling Factor: $selectedSpatialFactor")

  // 4. PIPELINE ORDERING
  val op1_choice: ProcessingStep.Type = ProcessingStep.SpatialSampling
  val op2_choice: ProcessingStep.Type = ProcessingStep.ColorQuantization
  val op3_choice: ProcessingStep.Type = ProcessingStep.ChromaSubsampling
  println(s"Selected Pipeline Order: $op1_choice -> $op2_choice -> $op3_choice")
  println("----------------------------------------------------")

  val outputBaseDirName = "APP_OUTPUT" 
  val pipelineOrderString = s"order-${op1_choice.toString.split('.').last.take(2)}-${op2_choice.toString.split('.').last.take(2)}-${op3_choice.toString.split('.').last.take(2)}"
  val outputFileNameSuffix = s"chroma4-${selectedChromaParamA}-${selectedChromaParamB}_Y${yTargetQuantBits}Cb${cbTargetQuantBits}Cr${crTargetQuantBits}_sf${selectedSpatialFactor}_${pipelineOrderString}"
  val outputPath = s"$outputBaseDirName/${imageName}_processed_${outputFileNameSuffix}.png"

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
      selectedChromaParamA,
      selectedChromaParamB,
      yTargetQuantBits,
      cbTargetQuantBits,
      crTargetQuantBits,
      selectedSpatialFactor,
      op1_choice, 
      op2_choice,
      op3_choice
    )
  }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.