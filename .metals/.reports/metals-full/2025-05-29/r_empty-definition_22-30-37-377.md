error id: file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/main/scala/jpeg/ChromaSubsampler.scala:`<none>`.
file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/main/scala/jpeg/ChromaSubsampler.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 449
uri: file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/main/scala/jpeg/ChromaSubsampler.scala
text:
```scala
package Chroma_Subsampling_Image_Compressor

import chisel3._
import chisel3.util._

object ChromaSubsamplingMode extends ChiselEnum {
  val CHROMA_444,
  CHROMA_422,
  CHROMA_420 = Value
}

class YCbCrPixel(val bitWidth: Int) extends Bundle { // Used by ChromaSubsampler & for casting
  val Y = UInt(bitWidth.W)
  val Cb = UInt(bitWidth.W)
  val Cr = UInt(bitWidth.W)
  override def toPrintable: Printable = p"YCbCr(Y:${Y}, Cb:${Cb}, Cr:${Cr})"
}

@@class ChromaSubsampler(
    val imageWidth: Int,
    val imageHeight: Int,
    val bitWidth: Int // Bit width of Y, Cb, Cr components
) extends Module {
  require(imageWidth > 0, "Image width must be positive")
  require(imageHeight > 0, "Image height must be positive")
  // PixelYCbCrBundle uses 8.W for its components.
  // This version of ChromaSubsampler must be instantiated with bitWidth = 8
  // if it's to connect directly to modules using PixelYCbCrBundle.
  require(bitWidth == 8, "bitWidth must be 8 to match PixelYCbCrBundle's component widths.")

  val io = IO(new Bundle {
    // Input YCbCr pixel stream, using PixelYCbCrBundle from PixelBundle.scala
    val dataIn = Flipped(Decoupled(new PixelYCbCrBundle())) // Changed to PixelYCbCrBundle
    // Chroma subsampling mode (e.g., 4:4:4, 4:2:2, 4:2:0)
    val mode = Input(ChromaSubsamplingMode())
    // Output YCbCr pixel stream (potentially with subsampled Cb, Cr)
    val dataOut = Decoupled(new PixelYCbCrBundle())      // Changed to PixelYCbCrBundle
  })

  // Registers to hold the output pixel data
  val yReg = Reg(UInt(bitWidth.W))
  val cbReg = Reg(UInt(bitWidth.W))
  val crReg = Reg(UInt(bitWidth.W))
  // Register to indicate if the output registers (yReg, cbReg, crReg) hold valid data
  val validReg = RegInit(false.B)

  // Registers to store the Cb and Cr values from the last relevant sampling point
  val lastCbReg = Reg(UInt(bitWidth.W))
  val lastCrReg = Reg(UInt(bitWidth.W))

  // Counters for current pixel column and row, advance when an input pixel is accepted (io.dataIn.fire)
  val (pixelCounter, pixelWrap) = Counter(io.dataIn.fire, imageWidth)
  val (lineCounter, lineWrap)   = Counter(io.dataIn.fire && pixelWrap, imageHeight)

  io.dataIn.ready := !validReg || io.dataOut.ready

  // Default assignments for output bits (assigned from registers)
  io.dataOut.bits.y  := yReg  // Changed to lowercase 'y'
  io.dataOut.bits.cb := cbReg // Changed to lowercase 'cb'
  io.dataOut.bits.cr := crReg // Changed to lowercase 'cr'
  io.dataOut.valid   := validReg

  // Logic for processing input pixels when they are valid and this module is ready
  when(io.dataIn.fire) { // io.dataIn.fire is (io.dataIn.valid && io.dataIn.ready)
    yReg := io.dataIn.bits.y // Changed to lowercase 'y'. Y component is always passed through
    validReg := true.B       // Output registers will now hold valid data

    // Determine current Cb and Cr values based on subsampling mode
    val currentCb = Wire(UInt(bitWidth.W))
    val currentCr = Wire(UInt(bitWidth.W))

    switch(io.mode) {
      is(ChromaSubsamplingMode.CHROMA_444) {
        // No subsampling: pass Cb and Cr directly
        currentCb := io.dataIn.bits.cb // Changed to lowercase 'cb'
        currentCr := io.dataIn.bits.cr // Changed to lowercase 'cr'
      }
      is(ChromaSubsamplingMode.CHROMA_422) {
        // Horizontal subsampling: Cb/Cr sampled at even columns
        when(pixelCounter % 2.U === 0.U) { // Even column
          currentCb := io.dataIn.bits.cb // Changed
          currentCr := io.dataIn.bits.cr // Changed
        } .otherwise { // Odd column
          currentCb := lastCbReg // Use Cb from previous (even) pixel
          currentCr := lastCrReg // Use Cr from previous (even) pixel
        }
      }
      is(ChromaSubsamplingMode.CHROMA_420) {
        // Horizontal and vertical subsampling: Cb/Cr sampled at (even_col, even_row)
        when(lineCounter % 2.U === 0.U) { // Even row
          when(pixelCounter % 2.U === 0.U) { // Even column
            currentCb := io.dataIn.bits.cb // Changed
            currentCr := io.dataIn.bits.cr // Changed
          } .otherwise { // Odd column, Even row
            currentCb := lastCbReg // Use Cb/Cr from the last (even_col, even_row) sample
            currentCr := lastCrReg
          }
        } .otherwise { // Odd row
          currentCb := lastCbReg // Use Cb/Cr from the last (even_col, even_row) sample
          currentCr := lastCrReg
        }
      }
    }
    cbReg := currentCb
    crReg := currentCr

    // Update lastCbReg and lastCrReg if the current input pixel IS a chroma sampling point for the active mode
    when( (io.mode === ChromaSubsamplingMode.CHROMA_444) ||
          (io.mode === ChromaSubsamplingMode.CHROMA_422 && (pixelCounter % 2.U === 0.U)) ||
          (io.mode === ChromaSubsamplingMode.CHROMA_420 && (pixelCounter % 2.U === 0.U) && (lineCounter % 2.U === 0.U)) ) {
      lastCbReg := io.dataIn.bits.cb // Changed
      lastCrReg := io.dataIn.bits.cr // Changed
    }
  } .elsewhen(io.dataOut.fire) { // Output was consumed by the downstream module
    validReg := false.B // Output registers are now free
  }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.