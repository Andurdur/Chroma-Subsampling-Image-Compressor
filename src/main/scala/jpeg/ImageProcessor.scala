// Should be in file: src/main/scala/jpeg/ImageProcessor.scala
package jpeg // Correct package based on directory structure

import chisel3._
import chisel3.util._
import Chroma_Subsampling_Image_Compressor._ // Import all from the other package

/**
 * Parameters for the full RGB→Chroma→Spatial image pipeline
 * @param width         input image width
 * @param height        input image height
 * @param factor        spatial downsample factor (1,2,4,8)
 * @param chromaMode    chroma subsampling mode (444,422,420) from Chroma_Subsampling_Image_Compressor
 */
case class ImageProcessorParams( // This case class definition is fine here
  width: Int,
  height: Int,
  factor: Int,
  chromaMode: ChromaSubsamplingMode.Type // ChromaSubsamplingMode will be found via import
) {
  require(width  > 0, "width must be positive")
  require(height > 0, "height must be positive")
  require(Set(1,2,4,8).contains(factor), "factor must be 1,2,4,8")
  require(width % factor == 0 && height % factor == 0, "dimensions must be divisible by factor")
}

class ImageProcessor(p: ImageProcessorParams) extends Module {
  val io = IO(new Bundle {
    // Input RGB pixel stream
    val in  = Flipped(Decoupled(new PixelBundle)) // PixelBundle found via import
    // Start of Frame signal
    val sof = Input(Bool())
    // End of Line signal
    val eol = Input(Bool())
    // Output YCbCr pixel stream (subsampled and spatially downsampled)
    val out = Decoupled(new PixelYCbCrBundle) // PixelYCbCrBundle found via import
  })

  // Stage 1: RGB to YCbCr conversion
  val rgb2ycbcr = Module(new RGB2YCbCr()) // RGB2YCbCr found via import
  rgb2ycbcr.io.in <> io.in

  // Stage 2: Chroma subsampling
  val chroma = Module(new ChromaSubsampler(p.width, p.height, 8)) // ChromaSubsampler found via import
  chroma.io.mode := p.chromaMode // p.chromaMode is of type ChromaSubsamplingMode.Type
  chroma.io.dataIn <> rgb2ycbcr.io.out

  //when(rgb2ycbcr.io.out.fire) {
  //  printf(p"RGB2YCbCr Out: Y=${rgb2ycbcr.io.out.bits.y} Cb=${rgb2ycbcr.io.out.bits.cb} Cr=${rgb2ycbcr.io.out.bits.cr}\n")
  //}

  // Stage 3: Spatial downsampling
  val spatial = Module(new SpatialDownsampler(p.width, p.height, p.factor)) // SpatialDownsampler found via import
  spatial.io.sof      := io.sof
  spatial.io.eol      := io.eol
  spatial.io.in <> chroma.io.dataOut

  //when(chroma.io.dataOut.fire) {
  //  printf(p"ChromaSubsampler Out: Y=${chroma.io.dataOut.bits.y} Cb=${chroma.io.dataOut.bits.cb} Cr=${chroma.io.dataOut.bits.cr} Mode=${chroma.io.mode}\n")
  //}

  io.out <> spatial.io.out

  //when(io.out.fire) {
  //  printf(p"ImageProcessor Out (SpatialDS Out): Y=${io.out.bits.y} Cb=${io.out.bits.cb} Cr=${io.out.bits.cr}\n")
  //}
}