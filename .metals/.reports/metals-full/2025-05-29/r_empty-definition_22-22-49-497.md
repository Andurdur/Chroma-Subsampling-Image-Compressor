error id: file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/main/scala/jpeg/ImageCompressorTop.scala:
file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/main/scala/jpeg/ImageCompressorTop.scala
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 248
uri: file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/main/scala/jpeg/ImageCompressorTop.scala
text:
```scala
// package Chroma_Subsampling_Image_Compressor

// import chisel3._
// import chisel3.util._

// class ImageCompressorTop(width: Int, height: Int, subMode: Int, downFactor: Int) extends Module {
//   val io = IO(new Bundle {
//     val in  = Flippe@@d(Decoupled(new PixelBundle))    // User's PixelBundle (r,g,b)
//     val out = Decoupled(new PixelYCbCrBundle) // User's PixelYCbCrBundle (y,cb,cr)
//     val sof = Input(Bool())
//     val eol = Input(Bool())
//   })

//   val fixedBitWidth = 8 

//   val toYC    = Module(new RGB2YCbCr) // User defined
//   val chroma  = Module(new ChromaSubsampler(
//     imageWidth = width,
//     imageHeight = height,
//     bitWidth = fixedBitWidth
//   ))
//   val spatial = Module(new SpatialDownsampler(width, height, downFactor)) // Uses PixelYCbCrBundle
//   val quant   = Module(new ColorQuantizer) // User defined

//   val selectedChromaMode = Wire(ChromaSubsamplingMode())
//   selectedChromaMode := MuxCase(
//     ChromaSubsamplingMode.CHROMA_444, 
//     Array(
//       (subMode.U === 0.U) -> ChromaSubsamplingMode.CHROMA_444,
//       (subMode.U === 1.U) -> ChromaSubsamplingMode.CHROMA_422,
//       (subMode.U === 2.U) -> ChromaSubsamplingMode.CHROMA_420
//     )
//   )
//   chroma.io.mode := selectedChromaMode

//   // --- Pipeline Connections with Debug Printfs ---

//   // Input to RGB2YCbCr
//   toYC.io.in <> io.in
//   when(io.in.fire) { // Data received by ImageCompressorTop from outside
//     printf(p"ICT_INPUT_FIRE: SOF=${io.sof} EOL=${io.eol} BitsIn=${io.in.bits}\n")
//   }

//   // RGB2YCbCr to SpatialDownsampler
//   // Assuming toYC.io.out is Decoupled(PixelYCbCrBundle) or compatible
//   toYC.io.out <> spatial.io.in
//   when(toYC.io.out.fire) { // Data leaves toYC and enters spatial
//     printf(p"ICT_TOYC_OUT_FIRE: BitsToSpatial=${toYC.io.out.bits}\n")
//   }
  
//   spatial.io.sof := io.sof // Pass through SOF/EOL to spatial
//   spatial.io.eol := io.eol

//   // SpatialDownsampler to ColorQuantizer
//   // Assuming spatial.io.out is Decoupled(PixelYCbCrBundle)
//   spatial.io.out <> quant.io.in
//   when(spatial.io.out.fire) { // Data leaves spatial and enters quant
//     printf(p"ICT_SPATIAL_OUT_FIRE: BitsToQuant=${spatial.io.out.bits}\n")
//   }

//   // ColorQuantizer to ChromaSubsampler
//   // quant.io.out is Decoupled(PixelYCbCrBundle)
//   // chroma.io.in needs YCbCrPixel(fixedBitWidth)
//   chroma.io.validIn := quant.io.out.valid
//   chroma.io.dataIn  := quant.io.out.bits.asTypeOf(new YCbCrPixel(fixedBitWidth))
//   quant.io.out.ready := io.out.ready 

//   when(quant.io.out.fire) { // Data leaves quant and is presented to chroma
//     printf(p"ICT_QUANT_OUT_FIRE: ToChromaValid=${chroma.io.validIn} BitsToChroma=${chroma.io.dataIn}\n")
//   }
  
//   // ChromaSubsampler Output to ImageCompressorTop Output
//   // chroma.io.dataOut is YCbCrPixel(fixedBitWidth)
//   // io.out.bits is PixelYCbCrBundle
//   io.out.valid := chroma.io.validOut
//   io.out.bits  := chroma.io.dataOut.asTypeOf(new PixelYCbCrBundle)

//   when(chroma.io.validOut && io.out.ready) { // When Chroma output is accepted by final consumer
//      printf(p"ICT_CHROMA_OUT_FIRE: BitsToICTOut=${chroma.io.dataOut}\n")
//   }
//   // Final output fire (redundant if above is used, but confirms top-level out fire)
//   // when(io.out.fire){
//   //    printf(p"ICT_FINAL_OUTPUT_FIRE: Bits=${io.out.bits}\n")
//   // }
// }

```


#### Short summary: 

empty definition using pc, found symbol in pc: 