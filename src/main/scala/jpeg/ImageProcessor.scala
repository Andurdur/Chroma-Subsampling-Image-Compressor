package jpeg 

import chisel3._
import chisel3.util._
import Chroma_Subsampling_Image_Compressor._ 

/**
 * Parameters for the full RGB -> Chroma -> Spatial image pipeline
 * @param width         input image width
 * @param height        input image height
 * @param factor        spatial downsample factor (1,2,4,8)
 * @param chromaMode    chroma subsampling mode (444,422,420) from Chroma_Subsampling_Image_Compressor
 */
case class ImageProcessorParams(
  width: Int,
  height: Int,
  factor: Int,
  chromaMode: ChromaSubsamplingMode.Type 
) {
  require(width  > 0, "width must be positive")
  require(height > 0, "height must be positive")
  require(Set(1,2,4,8).contains(factor), "factor must be 1,2,4,8")
  require(width % factor == 0 && height % factor == 0, "dimensions must be divisible by factor")
}

class ImageProcessor(p: ImageProcessorParams) extends Module {
  val io = IO(new Bundle {
    // Input RGB pixel stream
    val in  = Flipped(Decoupled(new PixelBundle))
    // Start of Frame signal
    val sof = Input(Bool())
    // End of Line signal
    val eol = Input(Bool())
    // Output YCbCr pixel stream (subsampled and spatially downsampled)
    val out = Decoupled(new PixelYCbCrBundle) 
  })

  // Stage 1: RGB to YCbCr conversion
  val rgb2ycbcr = Module(new RGB2YCbCr())
  rgb2ycbcr.io.in <> io.in

  // Stage 2: Chroma subsampling
  val chroma = Module(new ChromaSubsampler(p.width, p.height, 8)) 
  chroma.io.mode := p.chromaMode 
  chroma.io.dataIn <> rgb2ycbcr.io.out

  // Stage 3: Spatial downsampling
  val spatial = Module(new SpatialDownsampler(p.width, p.height, p.factor)) 
  spatial.io.sof      := io.sof
  spatial.io.eol      := io.eol
  spatial.io.in <> chroma.io.dataOut


  io.out <> spatial.io.out
}