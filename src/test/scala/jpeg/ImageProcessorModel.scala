//Code from https://github.com/erendn/chisel-image-processor/blob/main/src/test/scala/ImageProcessorModel.scala
//Using for image reading and writing

package jpeg // Updated package to match file path from error message

import java.io.File
import com.sksamuel.scrimage.{ImmutableImage, MutableImage}
import com.sksamuel.scrimage.nio.PngWriter
// import com.sksamuel.scrimage.filter.Filter // Filter was not used
import com.sksamuel.scrimage.pixels.Pixel

// ImageProcessorParams is defined in the 'jpeg' package (e.g. in ImageProcessor.scala),
// so no explicit import is needed if this file is also in 'package jpeg'.
// If ImageProcessorParams were in a sub-package like jpeg.defs, it would be 'import jpeg.defs.ImageProcessorParams'

// ChromaSubsamplingMode is part of the 'Chroma_Subsampling_Image_Compressor' package.
import Chroma_Subsampling_Image_Compressor.ChromaSubsamplingMode


object ImageProcessorModel {

  type PixelType = Seq[Int]
  type ImageType = Seq[Seq[PixelType]]

  def readImage(file: String): ImmutableImage = {
    ImmutableImage.loader().fromFile(file)
  }

  def writeImage(image: MutableImage, file: String): Unit = {
    val outputFile = new File(file)
    outputFile.getParentFile().mkdirs() // Create parent directories
    image.output(new PngWriter(), outputFile)
  }

  // This method now correctly uses ImageProcessorParams (from current jpeg package)
  def writeImage(image: Array[Pixel], p: ImageProcessorParams, file: String): Unit = {
     // Corrected: Use ImmutableImage.create
     val immutableTemp = ImmutableImage.create(p.width, p.height, image)
     val mutableImage = immutableTemp.copy() // .copy() returns a MutableImage
     ImageProcessorModel.writeImage(mutableImage, file)
   }

  /** Default to no chroma subsampling (4:4:4) when you only need SpatialDownsampler */
  // This method now correctly uses ImageProcessorParams (from current jpeg package)
  // and ChromaSubsamplingMode (imported from Chroma_Subsampling_Image_Compressor package)
  def getImageParams(image: ImmutableImage, numPixelsPerCycle: Int): ImageProcessorParams = {
    ImageProcessorParams(
      width      = image.width,
      height     = image.height,
      factor     = numPixelsPerCycle,
      chromaMode = ChromaSubsamplingMode.CHROMA_444 // Uses imported ChromaSubsamplingMode
    )
   }

  def getImagePixels(image: ImmutableImage): ImageType = {
    val pixelArray = Array.ofDim[PixelType](image.height, image.width)
    for (r <- 0 until image.height) {
      for (c <- 0 until image.width) {
        val pixel = image.pixel(c, r)
        pixelArray(r)(c) = Seq(pixel.red(), pixel.green(), pixel.blue())
      }
    }
    Seq.tabulate(image.height, image.width) { (i, j) => pixelArray(i)(j) }
  }
}
