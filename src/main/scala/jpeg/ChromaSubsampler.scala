package jpeg

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
    val bitWidth: Int
) extends Module {
  require(imageWidth > 0, "Image width must be positive")
  require(imageHeight > 0, "Image height must be positive")
  require(bitWidth > 0, "Bit width must be positive")

  val io = IO(new Bundle {
    val dataIn = Input(new YCbCrPixel(bitWidth)) // Expects YCbCrPixel
    val validIn = Input(Bool())
    val mode = Input(ChromaSubsamplingMode())
    val dataOut = Output(new YCbCrPixel(bitWidth)) // Outputs YCbCrPixel
    val validOut = Output(Bool())
  })

  val yOutReg  = Reg(UInt(bitWidth.W))
  val cbOutReg = Reg(UInt(bitWidth.W))
  val crOutReg = Reg(UInt(bitWidth.W))
  val lastCbReg = Reg(UInt(bitWidth.W))
  val lastCrReg = Reg(UInt(bitWidth.W))

  val (pixelCounter, pixelWrap) = Counter(io.validIn, imageWidth)
  val (lineCounter, lineWrap)   = Counter(io.validIn && pixelWrap, imageHeight)

  yOutReg  := io.dataIn.Y
  cbOutReg := lastCbReg
  crOutReg := lastCrReg

  when(io.validIn) {
    yOutReg  := io.dataIn.Y
    switch(io.mode) {
      is(ChromaSubsamplingMode.CHROMA_444) {
        cbOutReg  := io.dataIn.Cb
        crOutReg  := io.dataIn.Cr
        lastCbReg := io.dataIn.Cb
        lastCrReg := io.dataIn.Cr
      }
      is(ChromaSubsamplingMode.CHROMA_422) {
        when(pixelCounter % 2.U === 0.U) {
          cbOutReg  := io.dataIn.Cb
          crOutReg  := io.dataIn.Cr
          lastCbReg := io.dataIn.Cb
          lastCrReg := io.dataIn.Cr
        } .otherwise {
          cbOutReg  := lastCbReg
          crOutReg  := lastCrReg
        }
      }
      is(ChromaSubsamplingMode.CHROMA_420) {
        when(lineCounter % 2.U === 0.U) {
          when(pixelCounter % 2.U === 0.U) {
            cbOutReg  := io.dataIn.Cb
            crOutReg  := io.dataIn.Cr
            lastCbReg := io.dataIn.Cb
            lastCrReg := io.dataIn.Cr
          } .otherwise {
            cbOutReg  := lastCbReg
            crOutReg  := lastCrReg
          }
        } .otherwise {
          cbOutReg  := lastCbReg
          crOutReg  := lastCrReg
        }
      }
    }
  }

  io.dataOut.Y  := yOutReg
  io.dataOut.Cb := cbOutReg
  io.dataOut.Cr := crOutReg
  io.validOut := RegNext(io.validIn, init = false.B)

  when(io.validIn) {
    printf(p"ChromaSubsampler: Mode=${io.mode}, Pixel($pixelCounter,$lineCounter), ValidIn=${io.validIn} InData=${io.dataIn} -> OutData(Y=${yOutReg},Cb=${cbOutReg},Cr=${crOutReg}) next_validOut=${RegNext(io.validIn, false.B)} LastCbCr(Cb=${lastCbReg},Cr=${lastCrReg})\n")
  }
}
