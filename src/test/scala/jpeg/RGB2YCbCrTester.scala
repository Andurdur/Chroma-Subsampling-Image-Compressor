package Chroma_Subsampling_Image_Compressor 

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import jpeg.RGB2YCbCr

class RGB2YCbCrTester extends AnyFlatSpec with ChiselScalatestTester {
  "RGB2YCbCr" should "match the ReferenceModel" in {
    test(new RGB2YCbCr) { c =>
      c.io.out.ready.poke(true.B)
      val samples = Seq(
        ReferenceModel.PixelRGB(0,0,0),
        ReferenceModel.PixelRGB(255,255,255),
        ReferenceModel.PixelRGB(255,0,0),
        ReferenceModel.PixelRGB(0,255,0),
        ReferenceModel.PixelRGB(0,0,255)
      )
      for (p <- samples) {
        c.io.in.bits.r.poke(p.r.U)
        c.io.in.bits.g.poke(p.g.U)
        c.io.in.bits.b.poke(p.b.U)
        c.io.in.valid.poke(true.B)
        c.clock.step() // Step after inputs are valid and poked
        while(!c.io.out.valid.peek().litToBoolean) {
            c.clock.step()
        }
        c.io.out.bits.y.expect(ReferenceModel.rgb2ycbcr(p).y.U)
        c.io.out.bits.cb.expect(ReferenceModel.rgb2ycbcr(p).cb.U)
        c.io.out.bits.cr.expect(ReferenceModel.rgb2ycbcr(p).cr.U)
      }
      c.io.in.valid.poke(false.B) // Ensure valid is low at the end of the loop
    }
  }
}
