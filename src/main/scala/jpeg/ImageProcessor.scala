package Chroma_Subsampling_Image_Compressor

import chisel3._
import chisel3.util._

/** Modes for ChromaSubsampler enum */
import Chroma_Subsampling_Image_Compressor.ChromaSubsamplingMode

/**
 * Parameters for the full RGB→Chroma→Spatial image pipeline
 * @param width         input image width
 * @param height        input image height
 * @param factor        spatial downsample factor (1,2,4,8)
 * @param chromaMode    chroma subsampling mode (444,422,420)
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
    // End of Line signal (currently unused by SpatialDownsampler in this setup, but kept for interface consistency)
    val eol = Input(Bool())
    // Output YCbCr pixel stream (subsampled and spatially downsampled)
    val out = Decoupled(new PixelYCbCrBundle)
  })

  // Stage 1: RGB to YCbCr conversion
  val rgb2ycbcr = Module(new RGB2YCbCr())
  // Connect the input of ImageProcessor to the input of RGB2YCbCr module
  rgb2ycbcr.io.in <> io.in

  // Stage 2: Chroma subsampling
  // IMPORTANT: The third argument to ChromaSubsampler is bitWidth, not the downsampling factor.
  // Assuming 8-bit components for Y, Cb, Cr based on PixelBundle and PixelYCbCrBundle.
  val chroma = Module(new ChromaSubsampler(p.width, p.height, 8)) // Using 8 for bitWidth
  chroma.io.mode := p.chromaMode
  // Connect the output of RGB2YCbCr to the input of ChromaSubsampler
  // This handles .valid, .ready, and .bits.* connections automatically.
  chroma.io.dataIn <> rgb2ycbcr.io.out
  
  //debug
  when(rgb2ycbcr.io.out.fire) { // Or chroma.io.dataIn.fire
    printf(p"RGB2YCbCr Out: Y=${rgb2ycbcr.io.out.bits.y} Cb=${rgb2ycbcr.io.out.bits.cb} Cr=${rgb2ycbcr.io.out.bits.cr}\n")
  }

  // Stage 3: Spatial downsampling
  val spatial = Module(new SpatialDownsampler(p.width, p.height, p.factor))
  spatial.io.sof      := io.sof
  spatial.io.eol      := io.eol // eol is part of SpatialDownsampler's IO but might not be strictly needed for its current logic
  // Connect the output of ChromaSubsampler to the input of SpatialDownsampler
  // This handles .valid, .ready, and .bits.* connections automatically.
  // The problematic line `chroma.io.validOut := spatial.io.in.ready` is GONE
  // because this Decoupled connection handles the handshake.
  spatial.io.in <> chroma.io.dataOut

  //debug
  when(chroma.io.dataOut.fire) { // Or spatial.io.in.fire
    printf(p"ChromaSubsampler Out: Y=${chroma.io.dataOut.bits.y} Cb=${chroma.io.dataOut.bits.cb} Cr=${chroma.io.dataOut.bits.cr} Mode=${chroma.io.mode}\n")
  }

  // Connect the output of SpatialDownsampler to the top-level output of ImageProcessor
  io.out <> spatial.io.out
  
  //debug
  when(io.out.fire) {
    printf(p"ImageProcessor Out (SpatialDS Out): Y=${io.out.bits.y} Cb=${io.out.bits.cb} Cr=${io.out.bits.cr}\n")
  }
}