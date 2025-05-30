import chisel3._
import chisel3.util._

// If PixelBuffer and Convolution are in a different package, you might need imports:
// import your_package_name.PixelBuffer
// import your_package_name.Convolution

//############################################################################
// 0. Assuming these classes are defined elsewhere in your project (e.g., PixelBuffer.scala, Convolution.scala)
//    If not, you'll need to include their definitions.
//############################################################################

// From erendn/chisel-image-processor/blob/main/src/main/scala/PixelBuffer.scala
// Make sure this class is accessible
/*
class PixelBuffer(bitWidth: Int, kernelSize: Int, imageWidth: Int) extends Module {
  val io = IO(new Bundle {
    val dataIn = Input(UInt(bitWidth.W))
    val validIn = Input(Bool())
    val dataOut = Output(Vec(kernelSize, Vec(kernelSize, UInt(bitWidth.W))))
    val validOut = Output(Bool())
  })
  // ... implementation ...
}
*/

// From erendn/chisel-image-processor/blob/main/src/main/scala/Convolution.scala
// Make sure this class is accessible
/*
class Convolution(kernelSize: Int, bitWidth: Int, kernelWeights: List[List[Int]]) extends Module {
  val io = IO(new Bundle {
    val dataIn = Input(Vec(kernelSize, Vec(kernelSize, UInt(bitWidth.W))))
    val validIn = Input(Bool())
    // Example kernel IO, or it could be a parameter as in your original
    // val kernel = Input(Vec(kernelSize, Vec(kernelSize, SInt(bitWidth.W))))
    val dataOut = Output(UInt(bitWidth.W)) // Adjust type as needed (e.g., SInt)
    val validOut = Output(Bool())
  })
  // ... implementation ...
}
*/


//############################################################################
// 1. Pixel Bundle Definitions
//############################################################################

/**
 * Bundle for an RGB pixel.
 */
class RGBPixel(val bitWidth: Int) extends Bundle {
  val R = UInt(bitWidth.W)
  val G = UInt(bitWidth.W)
  val B = UInt(bitWidth.W)
}

/**
 * Bundle for a YCbCr pixel.
 */
class YCbCrPixel(val bitWidth: Int) extends Bundle {
  val Y = UInt(bitWidth.W)
  val Cb = UInt(bitWidth.W)
  val Cr = UInt(bitWidth.W)
}

//############################################################################
// 2. RGB to YCbCr Conversion Module (Placeholder)
//############################################################################

class RGBtoYCbCr(val bitWidth: Int) extends Module {
  val io = IO(new Bundle {
    val rgbIn = Input(new RGBPixel(bitWidth))
    val ycbcrOut = Output(new YCbCrPixel(bitWidth))
    val validIn = Input(Bool())
    val validOut = Output(Bool())
  })

  // === IMPORTANT: Placeholder Logic ===
  // Replace this with accurate RGB to YCbCr conversion.
  // Standard formulas involve floating point coefficients, requiring fixed-point
  // or scaled integer arithmetic and clamping for hardware implementation.
  // Example (conceptual, not hardware-ready without scaling/fixed-point):
  // Y  = 0.299*R + 0.587*G + 0.114*B
  // Cb = -0.1687*R - 0.3313*G + 0.5*B + 128
  // Cr = 0.5*R - 0.4187*G - 0.0813*B + 128

  val r_uint = io.rgbIn.R
  val g_uint = io.rgbIn.G
  val b_uint = io.rgbIn.B

  // Extremely simplified placeholder:
  val Y_calc  = g_uint
  val Cb_calc = Mux(b_uint > g_uint, b_uint - g_uint, g_uint - b_uint) // Example: |B-G|
  val Cr_calc = Mux(r_uint > g_uint, r_uint - g_uint, g_uint - r_uint) // Example: |R-G|

  // Ensure results are clamped/masked to bitWidth.
  // For real conversion, proper scaling and offset addition/clamping are vital.
  val maxVal = ((1 << bitWidth) - 1).U

  io.ycbcrOut.Y  := Y_calc & maxVal
  io.ycbcrOut.Cb := Cb_calc & maxVal
  io.ycbcrOut.Cr := Cr_calc & maxVal

  // Pass through valid signal (assuming 1-cycle latency for this placeholder stage)
  io.validOut := RegNext(io.validIn, init = false.B)

  printf(p"RGBtoYCbCr: ValidIn=${io.validIn} RGB(R=${io.rgbIn.R},G=${io.rgbIn.G},B=${io.rgbIn.B}) -> YCbCr(Y=${io.ycbcrOut.Y},Cb=${io.ycbcrOut.Cb},Cr=${io.ycbcrOut.Cr}) ValidOut=${io.validOut}\n")
}


//############################################################################
// 3. Chroma Subsampler Module
//############################################################################

class ChromaSubsampler(
    imageWidth: Int,
    imageHeight: Int,
    bitWidth: Int,
    mode: String = "4:2:0"
) extends Module {
  val io = IO(new Bundle {
    val dataIn = Input(new YCbCrPixel(bitWidth))
    val validIn = Input(Bool())
    val dataOut = Output(new YCbCrPixel(bitWidth))
    val validOut = Output(Bool())
  })

  val dataOutReg = Reg(new YCbCrPixel(bitWidth))
  val validOutReg = RegInit(false.B)

  val cbReg = Reg(UInt(bitWidth.W))
  val crReg = Reg(UInt(bitWidth.W))

  val (pixelCounter, pixelWrap) = Counter(io.validIn && validOutReg, imageWidth) // Count valid output pixels
  val (lineCounter, lineWrap)   = Counter(pixelWrap && io.validIn && validOutReg, imageHeight)


  when(io.validIn) {
    dataOutReg.Y := io.dataIn.Y

    mode match {
      case "4:4:4" =>
        dataOutReg.Cb := io.dataIn.Cb
        dataOutReg.Cr := io.dataIn.Cr
        cbReg := io.dataIn.Cb
        crReg := io.dataIn.Cr
      case "4:2:2" =>
        when(pixelCounter % 2.U === 0.U) {
          dataOutReg.Cb := io.dataIn.Cb
          dataOutReg.Cr := io.dataIn.Cr
          cbReg := io.dataIn.Cb
          crReg := io.dataIn.Cr
        }.otherwise {
          dataOutReg.Cb := cbReg
          dataOutReg.Cr := crReg
        }
      case "4:2:0" =>
        when(lineCounter % 2.U === 0.U) {
          when(pixelCounter % 2.U === 0.U) {
            dataOutReg.Cb := io.dataIn.Cb
            dataOutReg.Cr := io.dataIn.Cr
            cbReg := io.dataIn.Cb
            crReg := io.dataIn.Cr
          }.otherwise {
            dataOutReg.Cb := cbReg
            dataOutReg.Cr := crReg
          }
        }.otherwise {
          dataOutReg.Cb := cbReg
          dataOutReg.Cr := crReg
        }
      case _ => // Default to 4:4:4
        dataOutReg.Cb := io.dataIn.Cb
        dataOutReg.Cr := io.dataIn.Cr
        cbReg := io.dataIn.Cb
        crReg := io.dataIn.Cr
    }
  }

  // Subsampler introduces 1 cycle of latency due to dataOutReg
  validOutReg := io.validIn
  io.dataOut  := dataOutReg
  io.validOut := validOutReg

  printf(p"ChromaSubsampler: Mode=$mode, ValidIn=${io.validIn}, Pixel($pixelCounter, $lineCounter) YCbCrIn(Y=${io.dataIn.Y},Cb=${io.dataIn.Cb},Cr=${io.dataIn.Cr}) -> YCbCrOut(Y=${io.dataOut.Y},Cb=${io.dataOut.Cb},Cr=${io.dataOut.Cr}) ValidOut=${io.validOut}\n")
}


//############################################################################
// 4. Modified ImageProcessor Module
//############################################################################

class ImageProcessor(
    imageWidth: Int,
    imageHeight: Int,
    bitWidth: Int = 8,
    subsamplingMode: String = "4:2:0",
    kernelSize: Int = 3, // For PixelBuffer and Convolution
    kernelWeights: List[List[Int]] // For Convolution
) extends Module {
  val io = IO(new Bundle {
    // Inputs
    val rgbIn = Input(new RGBPixel(bitWidth))
    val validIn = Input(Bool())
    // val kernel = Input(Vec(kernelSize, Vec(kernelSize, SInt(bitWidth.W)))) // If kernel is an IO

    // Outputs
    // Output from chroma subsampling stage (for debugging or if needed)
    val ycbcrSubsampledOut = Output(new YCbCrPixel(bitWidth))
    val validSubsampledOut = Output(Bool())

    // Final processed output (e.g., Luma after convolution)
    // IMPORTANT: Adjust the type of processedLumaOut to match your Convolution module's output type
    val processedLumaOut = Output(UInt(bitWidth.W)) // Example: Assuming UInt output
    val validProcessedOut = Output(Bool())
  })

  // Stage 1: RGB to YCbCr Conversion
  val rgbToYCbCrConverter = Module(new RGBtoYCbCr(bitWidth))
  rgbToYCbCrConverter.io.rgbIn    := io.rgbIn
  rgbToYCbCrConverter.io.validIn  := io.validIn

  // Stage 2: Chroma Subsampling
  val chromaSubsampler = Module(new ChromaSubsampler(imageWidth, imageHeight, bitWidth, subsamplingMode))
  chromaSubsampler.io.dataIn      := rgbToYCbCrConverter.io.ycbcrOut
  chromaSubsampler.io.validIn     := rgbToYCbCrConverter.io.validOut

  // Output from subsampling stage
  io.ycbcrSubsampledOut           := chromaSubsampler.io.dataOut
  io.validSubsampledOut           := chromaSubsampler.io.validOut

  // Stage 3: Pixel Buffer for Luma (Y) channel
  // Ensure PixelBuffer class is defined and accessible
  val pixelBuffer = Module(new PixelBuffer(bitWidth, kernelSize, imageWidth))
  pixelBuffer.io.dataIn         := chromaSubsampler.io.dataOut.Y // Use Luma channel
  pixelBuffer.io.validIn        := chromaSubsampler.io.validOut

  // Stage 4: Convolution on Luma channel
  // Ensure Convolution class is defined and accessible
  val convolution = Module(new Convolution(kernelSize, bitWidth, kernelWeights))
  convolution.io.dataIn         := pixelBuffer.io.dataOut // dataOut from PixelBuffer is Vec[Vec[UInt]]
  convolution.io.validIn        := pixelBuffer.io.validOut
  // convolution.io.kernel      := io.kernel // If kernel is an IO

  // Final processed output
  // IMPORTANT: Ensure type compatibility between convolution.io.dataOut and io.processedLumaOut
  io.processedLumaOut           := convolution.io.dataOut.asUInt // Example: Cast if needed, or ensure types match
  io.validProcessedOut          := convolution.io.validOut

  // Printfs for top-level debugging
  printf(p"ImageProcessor TOP: ValidIn=${io.validIn} RGB(R=${io.rgbIn.R},G=${io.rgbIn.G},B=${io.rgbIn.B})\n")
  printf(p"  -> YCbCrSubsampled(Y=${io.ycbcrSubsampledOut.Y},Cb=${io.ycbcrSubsampledOut.Cb},Cr=${io.ycbcrSubsampledOut.Cr}) ValidSubsampled=${io.validSubsampledOut}\n")
  printf(p"  -> PixelBufferIn(Y=${pixelBuffer.io.dataIn},Valid=${pixelBuffer.io.validIn})\n")
  printf(p"  -> ConvolutionIn(Valid=${convolution.io.validIn})\n") // Note: convolution.io.dataIn is a Vec
  printf(p"  -> ProcessedLumaOut(Luma=${io.processedLumaOut},Valid=${io.validProcessedOut})\n")
}

//############################################################################
// Example Main for generating Verilog (Optional)
//############################################################################
object ImageProcessorMain extends App {
  val imageWidth = 64
  val imageHeight = 64
  val bitWidth = 8
  val subsamplingMode = "4:2:0"
  val kernelSize = 3
  // Example kernel weights (e.g., an identity or simple blur)
  val kernelWeights = List(
    List(0, 0, 0),
    List(0, 1, 0),
    List(0, 0, 0)
  )
  // If your Convolution module takes SInt weights, ensure they are SInts.

  println(s"Generating Verilog for ImageProcessor with W=$imageWidth, H=$imageHeight, BW=$bitWidth, Mode=$subsamplingMode, Kernel=$kernelSize")
  (new chisel3.stage.ChiselStage).emitSystemVerilog(
    new ImageProcessor(imageWidth, imageHeight, bitWidth, subsamplingMode, kernelSize, kernelWeights),
    firtoolOpts = Array("-o", "ImageProcessor.v", "--strip-debug-info", "--lower-memories") // Added --lower-memories
  )
  println("Verilog generation complete: ImageProcessor.v")
}
