package Chroma_Subsampling_Image_Compressor // Matching your provided package name

import chisel3._
import chisel3.util._

// Assuming the following are defined in this package or imported correctly:
// - class PixelBundle extends Bundle { ... }
// - class PixelYCbCrBundle extends Bundle { ... } // Used by ImageCompressorTop's IO and submodules
// - class RGB2YCbCr extends Module { ... }
// - class SpatialDownsampler(width: Int, height: Int, factor: Int) extends Module { ... }
// - class ColorQuantizer extends Module { ... }
// - class ChromaSubsampler(imageWidth: Int, imageHeight: Int, bitWidth: Int) extends Module { ... }
//   (And this ChromaSubsampler's IO now uses DecoupledIO for dataIn and dataOut)
// - object ChromaSubsamplingMode extends ChiselEnum { ... }

class ImageCompressorTop(width: Int, height: Int, subMode: Int, downFactor: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new PixelBundle))
    val out = Decoupled(new PixelYCbCrBundle) // Your PixelYCbCrBundle
    val sof = Input(Bool())
    val eol = Input(Bool())
  })

  val fixedBitWidth = 8 // Assuming this is consistent with your Bundle definitions

  val toYC    = Module(new RGB2YCbCr)
  val chroma  = Module(new ChromaSubsampler( // This instantiation seems fine
    imageWidth = width,
    imageHeight = height,
    bitWidth = fixedBitWidth
  ))
  val spatial = Module(new SpatialDownsampler(width, height, downFactor))
  val quant   = Module(new ColorQuantizer)

  val selectedChromaMode = Wire(ChromaSubsamplingMode()) // Ensure ChromaSubsamplingMode is in scope
  selectedChromaMode := MuxCase(
    ChromaSubsamplingMode.CHROMA_444, 
    Array(
      (subMode.U === 0.U) -> ChromaSubsamplingMode.CHROMA_444,
      (subMode.U === 1.U) -> ChromaSubsamplingMode.CHROMA_422,
      (subMode.U === 2.U) -> ChromaSubsamplingMode.CHROMA_420
    )
  )
  chroma.io.mode := selectedChromaMode // This connection to mode is fine

  // --- Pipeline Connections with Debug Printfs ---

  // Input to RGB2YCbCr
  toYC.io.in <> io.in
  when(io.in.fire) {
    printf(p"ICT_INPUT_FIRE: SOF=${io.sof} EOL=${io.eol} BitsIn=${io.in.bits}\n")
  }

  // RGB2YCbCr to SpatialDownsampler
  toYC.io.out <> spatial.io.in // Assuming toYC.io.out is Decoupled and type-compatible
  when(toYC.io.out.fire) {
    printf(p"ICT_TOYC_OUT_FIRE: BitsToSpatial=${toYC.io.out.bits}\n")
  }
  
  spatial.io.sof := io.sof
  spatial.io.eol := io.eol

  // SpatialDownsampler to ColorQuantizer
  spatial.io.out <> quant.io.in // Assuming spatial.io.out is Decoupled and type-compatible
  when(spatial.io.out.fire) {
    printf(p"ICT_SPATIAL_OUT_FIRE: BitsToQuant=${spatial.io.out.bits}\n")
  }

  // ColorQuantizer to ChromaSubsampler
  // Assuming quant.io.out is Decoupled(PixelYCbCrBundle)
  // and chroma.io.dataIn is Flipped(Decoupled(PixelYCbCrBundle))
  quant.io.out <> chroma.io.dataIn // Corrected: Use bulk connect

  when(chroma.io.dataIn.fire) { // Changed condition to chroma's input fire
    printf(p"ICT_QUANT_OUT_FIRE (To Chroma): Fire | BitsToChroma=${chroma.io.dataIn.bits}\n")
  }
  
  // ChromaSubsampler Output to ImageCompressorTop Output
  // Assuming chroma.io.dataOut is Decoupled(PixelYCbCrBundle)
  // and io.out is Decoupled(PixelYCbCrBundle)
  chroma.io.dataOut <> io.out // Corrected: Use bulk connect

  // Use io.out.fire for the printf, as this represents the final output transaction
  when(io.out.fire) { 
     printf(p"ICT_FINAL_OUTPUT_FIRE (from Chroma): Fire | Bits=${io.out.bits}\n")
  }
}
