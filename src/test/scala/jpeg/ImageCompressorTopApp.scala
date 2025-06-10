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
 * Now accepts command-line arguments for parameterization.
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

    val inputImage: ImmutableImage = ImageProcessorModel.readImage(inputImagePath)
    val imageWidth = inputImage.width
    val imageHeight = inputImage.height

    val spatialSamplingInPipeline = op1 == ProcessingStep.SpatialSampling || op2 == ProcessingStep.SpatialSampling || op3 == ProcessingStep.SpatialSampling
    val finalOutputWidth = if (spatialSamplingInPipeline) imageWidth / spatialFactorToUse else imageWidth
    val finalOutputHeight = if (spatialSamplingInPipeline) imageHeight / spatialFactorToUse else imageHeight

    if (spatialSamplingInPipeline && (imageWidth % spatialFactorToUse != 0 || imageHeight % spatialFactorToUse != 0)) {
        println(s"[WARN] Image dimensions (${imageWidth}x$imageHeight) are not perfectly divisible by spatialFactor ($spatialFactorToUse). SpatialDownsampler might truncate.")
    }

    val outputPixelData = ArrayBuffer[(Int, Int, Int)]()

    chiseltest.RawTester.test(
            new jpeg.ImageCompressorTop(
                imageWidth,
                imageHeight,
                chromaParamA,
                chromaParamB,
                yTargetBits,
                cbTargetBits,
                crTargetBits,
                spatialFactorToUse,
                op1,
                op2,
                op3
            ),
            Seq(WriteVcdAnnotation) // VCD will be generated in the test_run_dir
        ) { dut =>

      dut.io.out.ready.poke(true.B)
      dut.io.in.valid.poke(false.B)
      dut.io.sof.poke(false.B)
      dut.io.eol.poke(false.B)
      dut.clock.step(1)

      val inputDriver = fork {
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
      }

      var collectedPixels = 0
      val expectedOutputPixels = finalOutputWidth * finalOutputHeight
      var timeoutCycles = expectedOutputPixels * 40 + 10000 // Adjusted timeout

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

      inputDriver.join()
    }

    val outputMutableImage = ImmutableImage.filled(finalOutputWidth, finalOutputHeight, AwtColor.MAGENTA).copy()
    for (y <- 0 until finalOutputHeight) {
      for (x <- 0 until finalOutputWidth) {
        val pixelIndex = y * finalOutputWidth + x
        if (pixelIndex < outputPixelData.length) {
          val (r, g, b) = outputPixelData(pixelIndex)
          outputMutableImage.setColor(x, y, new com.sksamuel.scrimage.color.RGBColor(r, g, b, 255))
        }
      }
    }

    ImageProcessorModel.writeImage(outputMutableImage, outputImagePath)
  }

  // --- Main execution with command-line argument parsing ---
  // Create a mutable map from command line args for easy lookup
  val argsMap = args.sliding(2, 2).collect {
    case Array(key, value) if key.startsWith("--") => key -> value
  }.toMap

  // Helper to parse a string into a ProcessingStep.Type
  def parseProcessingStep(name: String): ProcessingStep.Type = {
    name.toLowerCase match {
      case "spatial" | "spatialsampling" => ProcessingStep.SpatialSampling
      case "color" | "colorquantization" => ProcessingStep.ColorQuantization
      case "chroma" | "chromasubsampling" => ProcessingStep.ChromaSubsampling
      case _ => throw new IllegalArgumentException(s"Unknown processing step: $name. Use 'spatial', 'color', or 'chroma'.")
    }
  }

  // Get parameters from map or use default values
  val inputPath = argsMap.getOrElse("--input", "test_images/in128x128.png")
  val selectedChromaParamA = argsMap.getOrElse("--a", "4").toInt
  val selectedChromaParamB = argsMap.getOrElse("--b", "4").toInt
  val yTargetQuantBits = argsMap.getOrElse("--yq", "8").toInt
  val cbTargetQuantBits = argsMap.getOrElse("--cbq", "8").toInt
  val crTargetQuantBits = argsMap.getOrElse("--crq", "8").toInt
  val selectedSpatialFactor = argsMap.getOrElse("--sf", "8").toInt
  val op1_choice = parseProcessingStep(argsMap.getOrElse("--op1", "spatial"))
  val op2_choice = parseProcessingStep(argsMap.getOrElse("--op2", "color"))
  val op3_choice = parseProcessingStep(argsMap.getOrElse("--op3", "chroma"))

  val imageName = new File(inputPath).getName.takeWhile(_ != '.')

  println("----------------------------------------------------")
  println("Image Compressor Application Parameters:")
  println("----------------------------------------------------")
  println(s"Input Image: $inputPath")
  println(s"Selected Chroma Subsampling (J:a:b): 4:$selectedChromaParamA:$selectedChromaParamB")
  println(s"Selected Quantization Bits (Y/Cb/Cr): $yTargetQuantBits/$cbTargetQuantBits/$crTargetQuantBits")
  println(s"Selected Spatial Downsampling Factor: $selectedSpatialFactor")
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
    println(s"Image processing complete. Output saved to: $outputPath")
  }
}
