package jpeg 

import java.io.File
import com.sksamuel.scrimage.{ImmutableImage, MutableImage}
import com.sksamuel.scrimage.nio.PngWriter
import com.sksamuel.scrimage.pixels.Pixel


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

  def writeImage(image: Array[Pixel], p: ImageProcessorParams, file: String): Unit = {
     val immutableTemp = ImmutableImage.create(p.width, p.height, image)
     val mutableImage = immutableTemp.copy() 
     ImageProcessorModel.writeImage(mutableImage, file)
   }

  /** * Gets ImageProcessorParams. Defaults to 4:4:4 chroma subsampling 
   * (param_a=4, param_b=4) if only spatial downsampling is the focus.
   */
  def getImageParams(image: ImmutableImage, numPixelsPerCycle: Int): ImageProcessorParams = {
    ImageProcessorParams(
      width        = image.width,
      height       = image.height,
      factor       = numPixelsPerCycle,
      chromaParamA = 4, // Default to 4:4:4 equivalent (no chroma subsampling)
      chromaParamB = 4  // Default to 4:4:4 equivalent
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
