error id: file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/test/scala/jpeg/ImageProcessorModel.scala:scrimage.
file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/test/scala/jpeg/ImageProcessorModel.scala
empty definition using pc, found symbol in pc: scrimage.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -com/sksamuel/scrimage.
	 -scala/Predef.com.sksamuel.scrimage.
offset: 310
uri: file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/test/scala/jpeg/ImageProcessorModel.scala
text:
```scala
//Code from https://github.com/erendn/chisel-image-processor/blob/main/src/test/scala/ImageProcessorModel.scala
//Using for image reading and writing

package Chroma_Subsampling_Image_Compressor

import java.io.File
import com.sksamuel.scrimage.{ImmutableImage, MutableImage}
import com.sksamuel.scrimag@@e.nio.PngWriter
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
    val imageObject = ImmutableImage.create(p.imageWidth, p.imageHeight, image)
    this.writeImage(imageObject, file)
  }

  def getImageParams(image: ImmutableImage, numPixelsPerCycle: Int): ImageProcessorParams = {
    new ImageProcessorParams(image.width, image.height, numPixelsPerCycle)
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
```


#### Short summary: 

empty definition using pc, found symbol in pc: scrimage.