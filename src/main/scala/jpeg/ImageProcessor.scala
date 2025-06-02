// Should be in file: src/main/scala/jpeg/ImageProcessor.scala
package jpeg 

import chisel3._
import chisel3.util._
// Import specific components needed from Chroma_Subsampling_Image_Compressor
import Chroma_Subsampling_Image_Compressor.{ChromaSubsampler, SpatialDownsampler, PixelBundle, PixelYCbCrBundle}
// RGB2YCbCr is assumed to be in the current 'jpeg' package

/**
 * Parameters for the RGB -> YCbCr -> Chroma Subsample -> Spatial Downsample pipeline.
 * @param width         Input image width.
 * @param height        Input image height.
 * @param factor        Spatial downsample factor (1, 2, 4, or 8).
 * @param chromaParamA  'a' in J:a:b chroma subsampling (4, 2, or 1 for J=4).
 * @param chromaParamB  'b' in J:a:b chroma subsampling (must be equal to chromaParamA or 0).
 */
case class ImageProcessorParams(
  width: Int,
  height: Int,
  factor: Int,
  // chromaMode: ChromaSubsamplingMode.Type, // REMOVED
  chromaParamA: Int,
  chromaParamB: Int
) {
  require(width  > 0, "width must be positive")
  require(height > 0, "height must be positive")
  require(Set(1,2,4,8).contains(factor), "factor must be 1, 2, 4, or 8")
  require(width % factor == 0 && height % factor == 0, "Image dimensions must be divisible by spatial downsampling factor.")
  
  require(Seq(4, 2, 1).contains(chromaParamA), s"chromaParamA must be 4, 2, or 1. Got $chromaParamA")
  require(chromaParamB == chromaParamA || chromaParamB == 0, s"chromaParamB must be equal to chromaParamA ($chromaParamA) or 0. Got $chromaParamB")
}

class ImageProcessor(p: ImageProcessorParams) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new PixelBundle())) 
    val sof = Input(Bool())
    val eol = Input(Bool())
    val out = Decoupled(new PixelYCbCrBundle()) 
  })

  val fixedInputBitWidth = 8 // Assuming 8-bit components for sub-modules

  // Stage 1: RGB to YCbCr conversion
  val rgb2ycbcr = Module(new RGB2YCbCr()) // RGB2YCbCr is in 'jpeg' package
  rgb2ycbcr.io.in <> io.in

  // Stage 2: Chroma subsampling
  // Instantiate ChromaSubsampler with new param_a and param_b
  val chroma = Module(new ChromaSubsampler(
    imageWidth = p.width, 
    imageHeight = p.height, 
    bitWidth = fixedInputBitWidth, // Pass the bitWidth
    param_a = p.chromaParamA,
    param_b = p.chromaParamB
  ))
  // chroma.io.mode := p.chromaMode // REMOVED - Mode is set by constructor params
  chroma.io.dataIn <> rgb2ycbcr.io.out

  // Stage 3: Spatial downsampling
  val spatial = Module(new SpatialDownsampler(p.width, p.height, p.factor))
  spatial.io.sof      := io.sof
  spatial.io.eol      := io.eol
  spatial.io.in <> chroma.io.dataOut // Output of ChromaSubsampler goes into SpatialDownsampler

  io.out <> spatial.io.out
}
