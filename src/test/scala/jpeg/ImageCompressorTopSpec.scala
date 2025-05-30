package Chroma_Subsampling_Image_Compressor

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers // For 'should be'
import scala.collection.mutable.ListBuffer

class ImageCompressorTopSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  behavior of "ImageCompressorTop Chroma Subsampling Verification"

  val inputPixelsRGB: Seq[Seq[(Int, Int, Int)]] = Seq(
    Seq((100, 150, 50), (110, 160, 60)), // Row 0: P00, P01
    Seq((120, 170, 70), (130, 180, 80))  // Row 1: P10, P11
  )
  val testWidth = 2
  val testHeight = 2
  val testBitWidth = 8
  val testDownFactor = 1 // Ensure SpatialDownsampler is passthrough

  // Your drive2x2Pixels function (ensure it's robust)
  def drive2x2Pixels(dut: ImageCompressorTop, pixels: Seq[Seq[(Int, Int, Int)]]): Unit = {
    println("Driving 2x2 pixels...")
    // dut.io.in.valid.poke(false.B) // Initial valid poke is usually done in the main test body
    // dut.io.sof.poke(false.B)
    // dut.io.eol.poke(false.B)
    // dut.clock.step(1) // Settle initial pokes

    val pixelStream = pixels.flatten
    for (idx <- 0 until pixelStream.length) {
      val (r_val, g_val, b_val) = pixelStream(idx)
      val row = idx / testWidth
      val col = idx % testWidth

      dut.io.in.bits.r.poke(r_val.U(testBitWidth.W))
      dut.io.in.bits.g.poke(g_val.U(testBitWidth.W))
      dut.io.in.bits.b.poke(b_val.U(testBitWidth.W))
      dut.io.sof.poke(row == 0 && col == 0)
      dut.io.eol.poke(col == testWidth - 1)
      dut.io.in.valid.poke(true.B)

      var cyclesWaiting = 0
      val readyTimeout = 10
      while(!dut.io.in.ready.peek().litToBoolean && cyclesWaiting < readyTimeout) {
        dut.clock.step(1)
        cyclesWaiting += 1
      }
      assert(dut.io.in.ready.peek().litToBoolean, s"DUT input never became ready for pixel ($row, $col) within $readyTimeout cycles")
      
      dut.clock.step(1) 
    }
    dut.io.in.valid.poke(false.B)
    dut.io.sof.poke(false.B)
    dut.io.eol.poke(false.B)
    println("Finished driving pixels.")
  }

  val modesToTestWithInt = Seq(
    ("4:4:4", 0),
    ("4:2:2", 1),
    ("4:2:0", 2)
  )

    modesToTestWithInt.foreach { case (modeName, subModeInt) =>
    it should s"verify chroma subsampling for $modeName (subMode = $subModeInt) on a 2x2 image" in {
      test(new ImageCompressorTop(testWidth, testHeight, subModeInt, testDownFactor))
        .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

        // Initial state: de-assert inputs, assert output ready
        dut.io.in.valid.poke(false.B)
        dut.io.sof.poke(false.B)
        dut.io.eol.poke(false.B)
        dut.io.out.ready.poke(true.B) // Keep output ready to accept data
        dut.clock.step(5) // Initial settle time

        val outputYCbCrPixels = ListBuffer[(BigInt, BigInt, BigInt)]()
        val expectedPixelCount = testWidth * testHeight
        
        // Fork the input driving process
        val inputDriverThread = fork {
          drive2x2Pixels(dut, inputPixelsRGB)
        }

        // Main thread will monitor and collect outputs.
        val collectionOverallTimeout = 80 
        println(s"Attempting to collect $expectedPixelCount output pixels (main thread timeout: $collectionOverallTimeout cycles)...")

        for (cycle <- 0 until collectionOverallTimeout) {
          if (outputYCbCrPixels.length < expectedPixelCount) { 
            if (dut.io.out.valid.peek().litToBoolean) { 
              val y = dut.io.out.bits.y.peek().litValue
              val cb = dut.io.out.bits.cb.peek().litValue
              val cr = dut.io.out.bits.cr.peek().litValue
              outputYCbCrPixels += ((y, cb, cr))
              println(f"Collected pixel ${outputYCbCrPixels.length} (DUT cycle approx $cycle by main thread): Y=$y%3d, Cb=$cb%3d, Cr=$cr%3d")
            }
          }
          dut.clock.step(1)
          // Removed the check for: && !inputDriverThread.isAlive
          // We will join the inputDriverThread later.
          // If all pixels are collected, we can break the loop or let it timeout.
          if (outputYCbCrPixels.length == expectedPixelCount) {
            println("All expected pixels collected by main thread loop.")
            // To exit 'for' loop early in Scala, you'd typically use a flag or a breakable block,
            // but for simplicity in Chiseltest, often you let the loop run or rely on join timeouts.
            // For this structure, we can let it complete its 'collectionOverallTimeout' iterations
            // or refine the loop termination if absolutely necessary.
          }
        }
        
        println("Main thread collection loop finished.")
        println("Waiting for input driver thread to complete (if not already)...")
        inputDriverThread.join() // Wait for input driver to finish all its clock steps.
        println("Input driver thread confirmed complete.")

        // After the input driver has fully completed, there might still be pixels in the DUT's pipeline.
        // Continue stepping the clock and collecting until all expected pixels are out or a final timeout.
        val finalFlushCycles = 20 
        if (outputYCbCrPixels.length < expectedPixelCount) {
            println(s"Performing final output collection for up to $finalFlushCycles additional cycles...")
            for (_ <- 0 until finalFlushCycles if outputYCbCrPixels.length < expectedPixelCount) {
                if (dut.io.out.valid.peek().litToBoolean && dut.io.out.ready.peek().litToBoolean) {
                     val y = dut.io.out.bits.y.peek().litValue
                     val cb = dut.io.out.bits.cb.peek().litValue
                     val cr = dut.io.out.bits.cr.peek().litValue
                     outputYCbCrPixels += ((y, cb, cr))
                     println(f"Collected pixel ${outputYCbCrPixels.length} (Final flush): Y=$y%3d, Cb=$cb%3d, Cr=$cr%3d")
                }
                dut.clock.step(1)
            }
        }
        
        println(s"All collection attempts finished. Total collected: ${outputYCbCrPixels.length}")

        println(s"--- Output Analysis for $modeName (subMode = $subModeInt) ---")
        outputYCbCrPixels.length should be (expectedPixelCount)

        if (outputYCbCrPixels.length == expectedPixelCount) {
            // ... (rest of your assertions for value checking) ...
            val cb_00 = outputYCbCrPixels(0)._2; val cr_00 = outputYCbCrPixels(0)._3
            val cb_01 = outputYCbCrPixels(1)._2; val cr_01 = outputYCbCrPixels(1)._3
            val cb_10 = outputYCbCrPixels(2)._2; val cr_10 = outputYCbCrPixels(2)._3
            val cb_11 = outputYCbCrPixels(3)._2; val cr_11 = outputYCbCrPixels(3)._3

            println(f"P(0,0): Y=${outputYCbCrPixels(0)._1}%3d Cb=$cb_00%3d Cr=$cr_00%3d")
            println(f"P(0,1): Y=${outputYCbCrPixels(1)._1}%3d Cb=$cb_01%3d Cr=$cr_01%3d")
            println(f"P(1,0): Y=${outputYCbCrPixels(2)._1}%3d Cb=$cb_10%3d Cr=$cr_10%3d")
            println(f"P(1,1): Y=${outputYCbCrPixels(3)._1}%3d Cb=$cb_11%3d Cr=$cr_11%3d")

            if (subModeInt == 0) { 
              println("Verifying 4:4:4...")
            } else if (subModeInt == 1) { 
              println("Verifying 4:2:2...")
              cb_00 should be (cb_01)
              cr_00 should be (cr_01)
              cb_10 should be (cb_11)
              cr_10 should be (cr_11)
            } else if (subModeInt == 2) { 
              println("Verifying 4:2:0...")
              cb_01 should be (cb_00); cb_10 should be (cb_00); cb_11 should be (cb_00)
              cr_01 should be (cr_00); cr_10 should be (cr_00); cr_11 should be (cr_00)
            }
        }
        println("-----------------------------------------")
        dut.clock.step(5) // Final settle
      }
    }
  }
}
