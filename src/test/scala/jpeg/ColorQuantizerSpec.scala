package Chroma_Subsampling_Image_Compressor

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.mutable.ListBuffer

// Assuming ColorQuantizer.scala (with QuantizationMode enum) and
// PixelYCbCrBundle (with y, cb, cr fields) are in this package or imported.

class ColorQuantizerSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  behavior of "ColorQuantizer"

  val originalBitWidth = 8

  // Software model to calculate expected quantized values
  def quantizePixelSW(
      y_in: Int,
      cb_in: Int,
      cr_in: Int,
      mode: QuantizationMode.Type
  ): (Int, Int, Int) = {
    
    var yTargetEffBits = originalBitWidth
    var cbTargetEffBits = originalBitWidth
    var crTargetEffBits = originalBitWidth

    if (mode == QuantizationMode.Q_16BIT) { // Y:6, Cb:5, Cr:5
      yTargetEffBits = 6
      cbTargetEffBits = 5
      crTargetEffBits = 5
    } else if (mode == QuantizationMode.Q_8BIT) { // Y:3, Cb:3, Cr:2
      yTargetEffBits = 3
      cbTargetEffBits = 3
      crTargetEffBits = 2
    }
    // For Q_24BIT, target bits remain originalBitWidth (e.g., 8)

    def quantChannel(value: Int, targetBits: Int, currentBits: Int): Int = {
      if (targetBits >= currentBits) return value // No quantization if target is same or more
      val shiftAmount = currentBits - targetBits
      (value >> shiftAmount) << shiftAmount
    }

    val y_q = quantChannel(y_in, yTargetEffBits, originalBitWidth)
    val cb_q = quantChannel(cb_in, cbTargetEffBits, originalBitWidth)
    val cr_q = quantChannel(cr_in, crTargetEffBits, originalBitWidth)
    (y_q, cb_q, cr_q)
  }

  // Sample input pixels (Y, Cb, Cr)
  val testPixels = Seq(
    (0, 0, 0),       // Black
    (255, 255, 255), // White
    (128, 128, 128), // Mid-gray
    (77, 150, 29),   // A specific color
    (200, 50, 220),  // Another color
    (16, 16, 16),    // Dark (min Y for some standards)
    (235, 240, 240)  // Bright (max Y/Cb/Cr for some standards)
  )

  val modesToTest = Seq(
    ("24-bit (Passthrough)", QuantizationMode.Q_24BIT),
    ("16-bit effective (Y6Cb5Cr5)", QuantizationMode.Q_16BIT),
    ("8-bit effective (Y3Cb3Cr2)", QuantizationMode.Q_8BIT)
  )

  modesToTest.foreach { case (modeName, quantModeEnum) =>
    it should s"correctly quantize pixels for $modeName mode" in {
      test(new ColorQuantizer(originalBitWidth))
        .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        
        dut.io.mode.poke(quantModeEnum)
        dut.io.in.valid.poke(false.B)
        dut.io.out.ready.poke(true.B) // Consumer is always ready
        dut.clock.step(2) // Initial settle

        val collectedOutputs = ListBuffer[(BigInt, BigInt, BigInt)]()
        val expectedOutputs = testPixels.map { px => 
          quantizePixelSW(px._1, px._2, px._3, quantModeEnum)
        }

        // Drive inputs and collect outputs sequentially
        for (idx <- 0 until testPixels.length) {
          val (y_in, cb_in, cr_in) = testPixels(idx)

          // Drive input
          dut.io.in.valid.poke(true.B)
          dut.io.in.bits.y.poke(y_in.U(originalBitWidth.W))
          dut.io.in.bits.cb.poke(cb_in.U(originalBitWidth.W))
          dut.io.in.bits.cr.poke(cr_in.U(originalBitWidth.W))

          // Wait for ready (ColorQuantizer is a simple buffer, should be ready quickly)
          var cyclesWaiting = 0
          while(!dut.io.in.ready.peek().litToBoolean && cyclesWaiting < 5) {
            dut.clock.step(1)
            cyclesWaiting += 1
          }
          assert(dut.io.in.ready.peek().litToBoolean, s"DUT input not ready for pixel $idx in $modeName")
          
          dut.clock.step(1) // Clock tick for the transaction
          dut.io.in.valid.poke(false.B) // De-assert valid after sending

          var outputCollectedForThisInput = false
          var collectionWaitCycles = 0
          val collectionTimeout = 10

          while(!outputCollectedForThisInput && collectionWaitCycles < collectionTimeout) {
            if (dut.io.out.valid.peek().litToBoolean) {
              val y_out = dut.io.out.bits.y.peek().litValue
              val cb_out = dut.io.out.bits.cb.peek().litValue
              val cr_out = dut.io.out.bits.cr.peek().litValue
              collectedOutputs += ((y_out, cb_out, cr_out))
              outputCollectedForThisInput = true
            }

            if (!outputCollectedForThisInput) { // Only step if we didn't collect this cycle
                 dut.clock.step(1)
            }
            collectionWaitCycles +=1
          }
          assert(outputCollectedForThisInput, s"Timeout waiting for output for pixel $idx in $modeName")
        }

        // Final assertions
        collectedOutputs.length should be (expectedOutputs.length)
        for (i <- collectedOutputs.indices) {
          val (dut_y, dut_cb, dut_cr) = collectedOutputs(i)
          val (exp_y, exp_cb, exp_cr) = expectedOutputs(i)
          
          dut_y.toInt should be (exp_y)
          dut_cb.toInt should be (exp_cb)
          dut_cr.toInt should be (exp_cr)
        }
        println(s"Test for $modeName passed. All pixel values correctly quantized.")
        dut.clock.step(5) // Final settle
      }
    }
  }
}


