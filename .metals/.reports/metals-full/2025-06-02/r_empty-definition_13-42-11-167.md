error id: file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/test/scala/jpeg/ColorQuantizerSpec.scala:`<none>`.
file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/test/scala/jpeg/ColorQuantizerSpec.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 5266
uri: file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/test/scala/jpeg/ColorQuantizerSpec.scala
text:
```scala
package jpeg 

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.mutable.ListBuffer

import Chroma_Subsampling_Image_Compressor.{ColorQuantizer, PixelYCbCrBundle}

class ColorQuantizerSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  behavior of "ColorQuantizer (Fully Parameterized)"

  val originalBitWidth = 8 // Assuming input components are 8-bit

  // Software model to calculate expected quantized values based on target bits
  def quantizePixelSW(
      y_in: Int,
      cb_in: Int,
      cr_in: Int,
      yTargetBits: Int,
      cbTargetBits: Int,
      crTargetBits: Int,
      originalBits: Int = 8
  ): (Int, Int, Int) = {
    
    def quantChannel(value: Int, targetBits: Int, currentBits: Int): Int = {
      if (targetBits < 1) throw new IllegalArgumentException("Target bits must be at least 1.")
      if (targetBits >= currentBits) return value // No quantization if target is same or more
      val shiftAmount = currentBits - targetBits
      (value >> shiftAmount) << shiftAmount
    }

    val y_q = quantChannel(y_in, yTargetBits, originalBits)
    val cb_q = quantChannel(cb_in, cbTargetBits, originalBits)
    val cr_q = quantChannel(cr_in, crTargetBits, originalBits)
    (y_q, cb_q, cr_q)
  }

  // Sample input pixels (Y, Cb, Cr)
  val testPixels = Seq(
    (0, 0, 0),       // Black
    (255, 255, 255), // White
    (128, 128, 128), // Mid-gray
    (77, 150, 29),   // A specific color (values are illustrative for YCbCr)
    (200, 50, 220),  // Another color
    (16, 16, 16),    // Dark
    (235, 240, 240)  // Bright
  )

  // Define test cases as tuples: (testNameSuffix, yBits, cbBits, crBits)
  val quantizationTestCases = Seq(
    ("Y8Cb8Cr8 (Passthrough)", 8, 8, 8),
    ("Y6Cb5Cr5 (16-bit effective)", 6, 5, 5),
    ("Y3Cb3Cr2 (8-bit effective)", 3, 3, 2),
    ("Y8Cb1Cr1 (Max Y, Min Chroma)", 8, 1, 1),
    ("Y1Cb8Cr8 (Min Y, Max Chroma)", 1, 8, 8),
    ("Y4Cb4Cr4 (All 4-bit)", 4, 4, 4)
  )

  quantizationTestCases.foreach { case (testName, yTargetBits, cbTargetBits, crTargetBits) =>
    it should s"correctly quantize pixels for $testName" in {
      test(new ColorQuantizer( // Instantiate with specific target bits
                yTargetBits = yTargetBits,
                cbTargetBits = cbTargetBits,
                crTargetBits = crTargetBits,
                originalBitWidth = originalBitWidth))
        .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        
        dut.io.in.valid.poke(false.B)
        dut.io.out.ready.poke(true.B) 
        dut.clock.step(2) 

        val collectedOutputs = ListBuffer[(BigInt, BigInt, BigInt)]()
        val expectedOutputs = testPixels.map { px => 
          quantizePixelSW(px._1, px._2, px._3, yTargetBits, cbTargetBits, crTargetBits, originalBitWidth)
        }

        for (idx <- 0 until testPixels.length) {
          val (y_in, cb_in, cr_in) = testPixels(idx)

          dut.io.in.valid.poke(true.B)
          dut.io.in.bits.y.poke(y_in.U(originalBitWidth.W))
          dut.io.in.bits.cb.poke(cb_in.U(originalBitWidth.W))
          dut.io.in.bits.cr.poke(cr_in.U(originalBitWidth.W))

          var cyclesWaiting = 0
          val readyTimeout = 10 // Increased timeout slightly
          while(!dut.io.in.ready.peek().litToBoolean && cyclesWaiting < readyTimeout) {
            dut.clock.step(1)
            cyclesWaiting += 1
          }
          assert(dut.io.in.ready.peek().litToBoolean, s"DUT input not ready for pixel $idx in $testName")
          
          dut.clock.step(1) 
          dut.io.in.valid.poke(false.B) 

          var outputCollectedForThisInput = false
          var collectionWaitCycles = 0
          val collectionTimeout = 15 // Increased timeout slightly

          while(!outputCollectedForThisInput && collectionWaitCycles < collectionTimeout) {
            if (dut.io.out.valid.peek().litToBoolean) {
              val y_out = dut.io.out.bits.y.peek().litValue
              val cb_out = dut.io.out.bits.cb.peek().litValue
              val cr_out = dut.io.out.bits.cr.peek().litValue
              collectedOutputs += ((y_out, cb_out, cr_out))
              outputCollectedForThisInput = true
            }

            if (!outputCollectedForThisInput) { 
                 dut.clock.step(1)
            }
            collectionWaitCycles +=1
          }
          assert(outputCollectedForThisInput, s"Timeout waiting for output for pixel $idx in $testName")
        }

        collectedOutputs.length should be (expectedOutputs.length)
        for (i <- collectedOutputs.indices) {
          val (dut_y, dut_cb, dut_cr) = collectedOutputs(i)
          val (exp_y, exp_cb, exp_cr) = expectedOutputs(i)
          
          withClue(s"Pixel $i, Y component: DUT=${dut_y}, EXP=${exp_y} for test $testName") {
            dut_y.toInt should be (exp_y)
          }
          withClue(s"Pixel $i, Cb component: DUT=${dut_cb}, EXP=${exp_cb} for test $testName") {
            dut_cb.toInt should be (exp_cb)
          }
          withClue(s"Pixel $i, Cr component: DUT=${dut_cr}, EXP=${exp_cr} for test $testName") {
            dut_cr.toInt should be (exp_cr)
          }
        }
        @@// println(s"Test for $testName passed. All pixel values correctly quantized.")
        dut.clock.step(5) 
      }
    }
  }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.