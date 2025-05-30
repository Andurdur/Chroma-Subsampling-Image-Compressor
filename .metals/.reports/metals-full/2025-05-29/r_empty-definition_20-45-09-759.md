error id: file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/main/scala/jpeg/ImageProcessor.scala:`<none>`.
file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/main/scala/jpeg/ImageProcessor.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -chisel3/downsampler.
	 -chisel3/util/downsampler.
	 -downsampler.
	 -scala/Predef.downsampler.
offset: 1540
uri: file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/main/scala/jpeg/ImageProcessor.scala
text:
```scala
package Chroma_Subsampling_Image_Compressor

import chisel3._
import chisel3.util._

/**
 * Parameters for image processing modules.
 */
case class ImageProcessorParams(
  imageWidth: Int,
  imageHeight: Int,
  pixelsPerCycle: Int
) {
  require(imageWidth > 0, "imageWidth must be positive")
  require(imageHeight > 0, "imageHeight must be positive")
  require(pixelsPerCycle > 0, "pixelsPerCycle must be positive")
  require(imageWidth % pixelsPerCycle == 0, "imageWidth must be divisible by pixelsPerCycle")

  val numRows   = imageHeight
  val numCols   = imageWidth
  val batchSize = pixelsPerCycle
}

/**
 * A thin wrapper to treat the SpatialDownsampler as an image processor.
 * Simply forwards Decoupled YCbCr streams through the downsampler.
 */
class ImageProcessor(p: ImageProcessorParams) extends Module {
  val io = IO(new Bundle {
    // Input YCbCr stream
    val in = Flipped(Decoupled(new Bundle {
      val y  = UInt(8.W)
      val cb = UInt(8.W)
      val cr = UInt(8.W)
    }))
    // Output downsampled YCbCr stream
    val out = Decoupled(new Bundle {
      val y  = UInt(8.W)
      val cb = UInt(8.W)
      val cr = UInt(8.W)
    })
  })

  // Instantiate the spatial downsampler
  val downsampler = Module(new SpatialDownsampler(p.numCols, p.numRows, p.batchSize))

  // Connect streaming interfaces
  downsampler.io.sof := false.B
  downsampler.io.eol := false.B
  // Connect the actual YCbCr streams
  downsampler.io.in  <> io.in
  io.out             <> downsampl@@er.io.out
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.