error id: file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/test/scala/jpeg/RGB2YCbCrTester.scala:`<none>`.
file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/test/scala/jpeg/RGB2YCbCrTester.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -chisel3/p/r/U.
	 -chisel3/p/r/U#
	 -chisel3/p/r/U().
	 -chiseltest/p/r/U.
	 -chiseltest/p/r/U#
	 -chiseltest/p/r/U().
	 -p/r/U.
	 -p/r/U#
	 -p/r/U().
	 -scala/Predef.p.r.U.
	 -scala/Predef.p.r.U#
	 -scala/Predef.p.r.U().
offset: 949
uri: file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/src/test/scala/jpeg/RGB2YCbCrTester.scala
text:
```scala
package Chroma_Subsampling_Image_Compressor // Assuming test stays in this package

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

// Import the RGB2YCbCr module from the jpeg package
import jpeg.RGB2YCbCr
// Assuming ReferenceModel is in the current package or correctly imported if elsewhere
// If ReferenceModel is also in 'jpeg' package, it would be: import jpeg.ReferenceModel

class RGB2YCbCrTester extends AnyFlatSpec with ChiselScalatestTester {
  "RGB2YCbCr" should "match the ReferenceModel" in {
    // Test with the imported RGB2YCbCr
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
        c.io.in.bits.r.poke(p.r.U@@)
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

```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.