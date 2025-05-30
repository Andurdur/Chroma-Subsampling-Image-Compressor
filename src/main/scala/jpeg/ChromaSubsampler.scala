package jpeg
import chisel3._
import chisel3.util._

//############################################################################
// 1. Define Chroma Subsampling Modes using ChiselEnum
//############################################################################
object ChromaSubsamplingMode extends ChiselEnum {
  val CHROMA_444, // No subsampling
  CHROMA_422, // Y, Cb/Cr, Y, Cb/Cr (Cb/Cr shared by 2 Y pixels horizontally)
  CHROMA_420 = Value // Y, Cb/Cr for a 2x2 block of Y pixels
  // You can add more modes here, e.g., CHROMA_411
}

//############################################################################
// 2. YCbCr Pixel Bundle Definition
//############################################################################
class YCbCrPixel(val bitWidth: Int) extends Bundle {
  val Y = UInt(bitWidth.W)
  val Cb = UInt(bitWidth.W)
  val Cr = UInt(bitWidth.W)

  // Helper for printing, not synthesizable as-is in all contexts directly
  override def toPrintable: Printable = {
    p"YCbCr(Y:${Y}, Cb:${Cb}, Cr:${Cr})"
  }
}

//############################################################################
// 3. ChromaSubsampler Module
//############################################################################
class ChromaSubsampler(
    val imageWidth: Int,   // Width of the image in pixels
    val imageHeight: Int,  // Height of the image in pixels
    val bitWidth: Int      // Bit width for each Y, Cb, Cr component
) extends Module {
  require(imageWidth > 0, "Image width must be positive")
  require(imageHeight > 0, "Image height must be positive")
  require(bitWidth > 0, "Bit width must be positive")

  val io = IO(new Bundle {
    val dataIn = Input(new YCbCrPixel(bitWidth))
    val validIn = Input(Bool())
    val mode = Input(ChromaSubsamplingMode()) // Selects the subsampling version

    val dataOut = Output(new YCbCrPixel(bitWidth))
    val validOut = Output(Bool())
  })

  // Internal registers for holding the output pixel and the last sampled Cb/Cr values
  val yOutReg  = Reg(UInt(bitWidth.W))
  val cbOutReg = Reg(UInt(bitWidth.W))
  val crOutReg = Reg(UInt(bitWidth.W))

  val lastCbReg = Reg(UInt(bitWidth.W)) // Stores the Cb value to be repeated
  val lastCrReg = Reg(UInt(bitWidth.W)) // Stores the Cr value to be repeated

  // Counters for pixel and line position
  // These counters advance only when valid input data is being processed.
  val (pixelCounter, pixelWrap) = Counter(io.validIn, imageWidth)
  val (lineCounter, lineWrap)   = Counter(io.validIn && pixelWrap, imageHeight)

  // Default assignments (passthrough Y, hold Cb/Cr)
  // Ensure Regs are only updated if validIn is true, otherwise they hold.
  // This is implicit if connections are inside a `when(io.validIn)` block for these regs.
  // However, for clarity on what yOutReg gets if not validIn:
  yOutReg := io.dataIn.Y // This will take io.dataIn.Y when io.validIn is true, due to outer when block
  cbOutReg := lastCbReg
  crOutReg := lastCrReg

  // Logic for different subsampling modes
  when(io.validIn) {
    yOutReg  := io.dataIn.Y // Y is always taken from the current input when valid

    switch(io.mode) {
      is(ChromaSubsamplingMode.CHROMA_444) {
        cbOutReg  := io.dataIn.Cb
        crOutReg  := io.dataIn.Cr
        lastCbReg := io.dataIn.Cb // Update last sampled Cb/Cr
        lastCrReg := io.dataIn.Cr
      }
      is(ChromaSubsamplingMode.CHROMA_422) {
        // Sample Cb, Cr on even-indexed pixels (0, 2, 4, ...)
        when(pixelCounter % 2.U === 0.U) {
          cbOutReg  := io.dataIn.Cb
          crOutReg  := io.dataIn.Cr
          lastCbReg := io.dataIn.Cb
          lastCrReg := io.dataIn.Cr
        } .otherwise {
          // Odd-indexed pixels reuse the Cb/Cr from the previous (even) pixel
          cbOutReg  := lastCbReg
          crOutReg  := lastCrReg
        }
      }
      is(ChromaSubsamplingMode.CHROMA_420) {
        // Sample Cb, Cr on even-indexed lines AND even-indexed pixels
        when(lineCounter % 2.U === 0.U) {         // Even lines (0, 2, ...)
          when(pixelCounter % 2.U === 0.U) {     // Even pixels (0, 2, ...) on even lines
            cbOutReg  := io.dataIn.Cb
            crOutReg  := io.dataIn.Cr
            lastCbReg := io.dataIn.Cb
            lastCrReg := io.dataIn.Cr
          } .otherwise {                          // Odd pixels (1, 3, ...) on even lines
            cbOutReg  := lastCbReg // Reuse Cb/Cr from the previous even pixel on this line
            crOutReg  := lastCrReg
          }
        } .otherwise {                            // Odd lines (1, 3, ...)
          // For odd lines, reuse the Cb/Cr values sampled from the
          // previous even line's even pixel (top-left of the 2x2 Y block).
          cbOutReg  := lastCbReg
          crOutReg  := lastCrReg
        }
      }
    }
  } .otherwise { // if !io.validIn, ensure registers hold their values (default Chisel Reg behavior)
      // yOutReg, cbOutReg, crOutReg, lastCbReg, lastCrReg will hold.
      // No explicit assignment needed here for registers to hold.
  }


  // Output assignment
  io.dataOut.Y  := yOutReg
  io.dataOut.Cb := cbOutReg
  io.dataOut.Cr := crOutReg

  // The module has a 1-cycle latency due to the output registers.
  io.validOut := RegNext(io.validIn, init = false.B)

  // Printfs for debugging
  when(io.validIn) {
    printf(p"ChromaSubsampler: Mode=${io.mode}, Pixel($pixelCounter, $lineCounter), ValidIn=${io.validIn}\n")
    printf(p"  In : ${io.dataIn}\n")
    printf(p"  Out: YCbCr(Y=${yOutReg}, Cb=${cbOutReg}, Cr=${crOutReg}) (ValidOut for next cycle=${RegNext(io.validIn, false.B)})\n") // Print registered values
    printf(p"  LastCbCr: Cb=${lastCbReg} Cr=${lastCrReg}\n")
  }
}

//############################################################################
// 4. Main object for Verilog generation (Corrected)
//############################################################################
object ChromaSubsamplerMain extends App {
  val imageWidth = 640 // Example width
  val imageHeight = 480 // Example height
  val bitWidth = 8    // 8-bit components

  println(s"Generating Verilog for ChromaSubsampler (W=$imageWidth, H=$imageHeight, BW=$bitWidth)")

  (new chisel3.stage.ChiselStage).emitSystemVerilog(
    gen = new ChromaSubsampler(imageWidth, imageHeight, bitWidth), // The module to generate
    args = Array( // Pass options as an Array[String]
      "--output-file", "ChromaSubsampler.v", // Specifies the output Verilog filename
      "--strip-debug-info"                   // This is a Firtool option to remove debug info
    )
  )
  println("Verilog generation complete: ChromaSubsampler.v")
}
