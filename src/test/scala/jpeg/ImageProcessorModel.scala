//Code from https://github.com/erendn/chisel-image-processor/blob/main/src/test/scala/ImageProcessorModel.scala
//Using for image reading and writing

package Chroma_Subsampling_Image_Compressor

import java.io.File
import com.sksamuel.scrimage.{ImmutableImage, MutableImage}
import com.sksamuel.scrimage.nio.PngWriter
import com.sksamuel.scrimage.filter.Filter
import com.sksamuel.scrimage.pixels.Pixel

object ImageProcessorModel {

  type PixelType = Seq[Int]
  type ImageType = Seq[Seq[PixelType]]

  def readImage(file: String): ImmutableImage = {
    ImmutableImage.loader().fromFile(file)
  }

  def applyFilter(image: ImmutableImage, filter: Filter): ImmutableImage = {
    image.filter(filter)
  }

  def writeImage(image: MutableImage, file: String): Unit = {
    val outputFile = new File(file)
    outputFile.getParentFile().mkdirs() // Create parent directories
    image.output(new PngWriter(), outputFile)
  }

  def writeImage(image: Array[Pixel], p: ImageProcessorParams, file: String): Unit = {
     val imageObject = ImmutableImage.create(p.width, p.height, image)
     this.writeImage(imageObject, file)
   }

  /** Default to no chroma subsampling (4:4:4) when you only need SpatialDownsampler */
  def getImageParams(image: ImmutableImage, numPixelsPerCycle: Int): ImageProcessorParams = {
    ImageProcessorParams(
      width      = image.width,
      height     = image.height,
      factor     = numPixelsPerCycle,
      chromaMode = ChromaSubsamplingMode.CHROMA_444
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