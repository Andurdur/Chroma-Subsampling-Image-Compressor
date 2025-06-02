// Should be in file: src/main/scala/jpeg/ImageCompressorTop.scala
package jpeg

import chisel3._
import chisel3.util._
import Chroma_Subsampling_Image_Compressor._ // For PixelBundle, Enums, and Sub-modules

class ImageCompressorTop(
    width: Int,
    height: Int,
    chromaModeSelect: ChromaSubsamplingMode.Type, // Parameter for Chroma mode
    quantModeSelect: QuantizationMode.Type,   // Parameter for Quantization mode
    downFactor: Int                           // Parameter for Spatial Downsampling
) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new PixelBundle()))
    val out = Decoupled(new PixelYCbCrBundle())
    val sof = Input(Bool())
    val eol = Input(Bool())
  })

  val fixedBitWidth = 8

  // Instantiate sub-modules
  // Ensure these modules are correctly defined in their respective packages
  val toYC    = Module(new RGB2YCbCr()) // Assumed to be in 'jpeg' package
  val chroma  = Module(new ChromaSubsampler(
    imageWidth = width,
    imageHeight = height,
    bitWidth = fixedBitWidth
  )) // From Chroma_Subsampling_Image_Compressor package
  val spatial = Module(new SpatialDownsampler(width, height, downFactor)) // From Chroma_Subsampling_Image_Compressor package
  val quant   = Module(new ColorQuantizer(originalBitWidth = fixedBitWidth)) // From Chroma_Subsampling_Image_Compressor package

  // Directly assign modes from input parameters
  chroma.io.mode := chromaModeSelect
  quant.io.mode  := quantModeSelect

  // Pipeline connections: RGB -> YCbCr -> Spatial Downsample -> Quantize -> Chroma Subsample -> Output
  toYC.io.in <> io.in
  
  toYC.io.out <> spatial.io.in
  spatial.io.sof := io.sof // Pass SOF to spatial downsampler
  spatial.io.eol := io.eol // Pass EOL to spatial downsampler
  
  spatial.io.out <> quant.io.in
  quant.io.out <> chroma.io.dataIn
  chroma.io.dataOut <> io.out

}
