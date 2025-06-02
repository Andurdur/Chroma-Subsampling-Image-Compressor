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

class ChromaSubsampler(
    val imageWidth: Int,
    val imageHeight: Int,
    val bitWidth: Int // Bit width of Y, Cb, Cr components
) extends Module {
  require(imageWidth > 0, "Image width must be positive")
  require(imageHeight > 0, "Image height must be positive")
  require(bitWidth == 8, "bitWidth must be 8 to match PixelYCbCrBundle's component widths.")

  val io = IO(new Bundle {
    val dataIn = Flipped(Decoupled(new PixelYCbCrBundle()))
    val mode = Input(ChromaSubsamplingMode())
    val dataOut = Decoupled(new PixelYCbCrBundle()) 
  })

  // Registers to hold the output pixel data
  val yReg = Reg(UInt(bitWidth.W))
  val cbReg = Reg(UInt(bitWidth.W))
  val crReg = Reg(UInt(bitWidth.W))
  // Register to indicate if the output registers (yReg, cbReg, crReg) hold valid data
  val validReg = RegInit(false.B)

  // Registers to store the Cb and Cr values from the last relevant sampling point
  val lastCbReg = RegInit(0.U(bitWidth.W))
  val lastCrReg = RegInit(0.U(bitWidth.W)) 


  // Counters for current pixel column and row, advance when an input pixel is accepted (io.dataIn.fire)
  val (pixelCounter, pixelWrap) = Counter(io.dataIn.fire, imageWidth)
  val (lineCounter, lineWrap)   = Counter(io.dataIn.fire && pixelWrap, imageHeight)

  io.dataIn.ready := !validReg || io.dataOut.ready

  // Default assignments for output bits (assigned from registers)
  io.dataOut.bits.y  := yReg
  io.dataOut.bits.cb := cbReg
  io.dataOut.bits.cr := crReg
  io.dataOut.valid   := validReg

  // Logic for processing input pixels when they are valid and this module is ready
  when(io.dataIn.fire) { // io.dataIn.fire is (io.dataIn.valid && io.dataIn.ready)
    yReg := io.dataIn.bits.y // Y component is always passed through
    validReg := true.B       // Output registers will now hold valid data

    // Determine current Cb and Cr values based on subsampling mode
    val currentCb = WireDefault(lastCbReg) 
    val currentCr = WireDefault(lastCrReg)


    switch(io.mode) {
      is(ChromaSubsamplingMode.CHROMA_444) {
        // No subsampling: pass Cb and Cr directly
        currentCb := io.dataIn.bits.cb
        currentCr := io.dataIn.bits.cr
      }
      is(ChromaSubsamplingMode.CHROMA_422) {
        // Horizontal subsampling: Cb/Cr sampled at even columns
        when(pixelCounter % 2.U === 0.U) { // Even column
          currentCb := io.dataIn.bits.cb
          currentCr := io.dataIn.bits.cr
        } .otherwise { // Odd column
          // currentCb and currentCr already defaulted to lastCbReg/lastCrReg
        }
      }
      is(ChromaSubsamplingMode.CHROMA_420) {
        // Horizontal and vertical subsampling: Cb/Cr sampled at (even_col, even_row)
        when(lineCounter % 2.U === 0.U) { // Even row
          when(pixelCounter % 2.U === 0.U) { // Even column
            currentCb := io.dataIn.bits.cb
            currentCr := io.dataIn.bits.cr
          } .otherwise { // Odd column, Even row
            // currentCb and currentCr already defaulted to lastCbReg/lastCrReg
          }
        } .otherwise { // Odd row
          // currentCb and currentCr already defaulted to lastCbReg/lastCrReg
        }
      }
    }
    cbReg := currentCb
    crReg := currentCr

    // Update lastCbReg and lastCrReg if the current input pixel IS a chroma sampling point for the active mode
    when( (io.mode === ChromaSubsamplingMode.CHROMA_444) ||
          (io.mode === ChromaSubsamplingMode.CHROMA_422 && (pixelCounter % 2.U === 0.U)) ||
          (io.mode === ChromaSubsamplingMode.CHROMA_420 && (pixelCounter % 2.U === 0.U) && (lineCounter % 2.U === 0.U)) ) {
      lastCbReg := io.dataIn.bits.cb
      lastCrReg := io.dataIn.bits.cr
    }
  } .elsewhen(io.dataOut.fire) { // Output was consumed by the downstream module
    validReg := false.B // Output registers are now free
  }
}
