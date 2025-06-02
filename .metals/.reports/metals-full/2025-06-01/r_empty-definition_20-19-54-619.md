error id: file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/main/scala/jpeg/ImageCompressorTop.scala:`<none>`.
file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/main/scala/jpeg/ImageCompressorTop.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -chisel3/MuxCase.
	 -chisel3/MuxCase#
	 -chisel3/MuxCase().
	 -chisel3/util/MuxCase.
	 -chisel3/util/MuxCase#
	 -chisel3/util/MuxCase().
	 -MuxCase.
	 -MuxCase#
	 -MuxCase().
	 -scala/Predef.MuxCase.
	 -scala/Predef.MuxCase#
	 -scala/Predef.MuxCase().
offset: 876
uri: file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/main/scala/jpeg/ImageCompressorTop.scala
text:
```scala
package Chroma_Subsampling_Image_Compressor

import chisel3._
import chisel3.util._

class ImageCompressorTop(width: Int, height: Int, subMode: Int, downFactor: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new PixelBundle))
    val out = Decoupled(new PixelYCbCrBundle)
    val sof = Input(Bool())
    val eol = Input(Bool())
  })

  val fixedBitWidth = 8 

  val toYC    = Module(new RGB2YCbCr)
  val chroma  = Module(new ChromaSubsampler(
    imageWidth = width,
    imageHeight = height,
    bitWidth = fixedBitWidth
  ))
  val spatial = Module(new SpatialDownsampler(width, height, downFactor))
  val quant   = Module(new ColorQuantizer(originalBitWidth = fixedBitWidth)) // Instantiate ColorQuantizer

  // --- Mode Selection for Chroma Subsampler ---
  val selectedChromaMode = Wire(ChromaSubsamplingMode())
  selectedChromaMode := Mu@@xCase(
    ChromaSubsamplingMode.CHROMA_444, 
    Array(
      (subMode.U === 0.U) -> ChromaSubsamplingMode.CHROMA_444,
      (subMode.U === 1.U) -> ChromaSubsamplingMode.CHROMA_422,
      (subMode.U === 2.U) -> ChromaSubsamplingMode.CHROMA_420
    )
  )
  chroma.io.mode := selectedChromaMode


  val selectedQuantMode = Wire(QuantizationMode())
  selectedQuantMode := MuxCase(
    QuantizationMode.Q_24BIT, 
    Array(

      (subMode.U === 0.U) -> QuantizationMode.Q_24BIT,
      (subMode.U === 1.U) -> QuantizationMode.Q_16BIT,
      (subMode.U === 2.U) -> QuantizationMode.Q_8BIT
    )
  )
  quant.io.mode := selectedQuantMode // Connect the mode to the quantizer instance



  toYC.io.in <> io.in
  when(io.in.fire) {
    printf(p"ICT_INPUT_FIRE: SOF=${io.sof} EOL=${io.eol} BitsIn=${io.in.bits}\n")
  }

  toYC.io.out <> spatial.io.in
  when(toYC.io.out.fire) {
    printf(p"ICT_TOYC_OUT_FIRE: BitsToSpatial=${toYC.io.out.bits}\n")
  }
  
  spatial.io.sof := io.sof
  spatial.io.eol := io.eol

  spatial.io.out <> quant.io.in
  when(spatial.io.out.fire) {
    printf(p"ICT_SPATIAL_OUT_FIRE: BitsToQuant=${spatial.io.out.bits}\n")
  }

  quant.io.out <> chroma.io.dataIn // If ChromaSubsampler IO uses Decoupled(PixelYCbCrBundle)

  when(quant.io.out.fire) { // Or when(chroma.io.dataIn.fire)
    printf(p"ICT_QUANT_OUT_FIRE (To Chroma): Fire | BitsToChroma=${chroma.io.dataIn.bits}\n")
  }
  
  chroma.io.dataOut <> io.out // If ChromaSubsampler IO uses Decoupled(PixelYCbCrBundle)

  when(io.out.fire){ // Data leaves ImageCompressorTop
     printf(p"ICT_FINAL_OUTPUT_FIRE (from Chroma): Fire | Bits=${io.out.bits}\n")
  }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.