package jpeg // Assuming this file is in the 'jpeg' package

import java.io.File
import com.sksamuel.scrimage.{ImmutableImage, MutableImage}
import com.sksamuel.scrimage.nio.PngWriter
import com.sksamuel.scrimage.pixels.Pixel

// ImageProcessorParams is defined in the 'jpeg' package (in ImageProcessor.scala)
// ChromaSubsamplingMode enum is no longer used by ImageProcessorParams for configuration.
// If ChromaSubsamplingMode enum itself is needed for other utilities *within this file only*
// and is defined in Chroma_Subsampling_Image_Compressor, it could be imported,
// but it's not used by getImageParams anymore.

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

  // This method now correctly uses the updated ImageProcessorParams
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
      // chromaMode = ChromaSubsamplingMode.CHROMA_444, // REMOVED
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
