package jpeg

import chisel3._
import chisel3.util.{Decoupled, Queue} 
import Chroma_Subsampling_Image_Compressor._ 

object ProcessingStep extends ChiselEnum {
  val NoOp, SpatialSampling, ColorQuantization, ChromaSubsampling = Value
}

class ImageCompressorTop(
    width: Int,
    height: Int,
    // Parameters for specific operations
    chroma_param_a_config: Int, // J:a:b 'a' parameter (4, 2, or 1)
    chroma_param_b_config: Int, // J:a:b 'b' parameter (equal to 'a', or 0)
    yTargetQuantBitsConfig: Int,
    cbTargetQuantBitsConfig: Int,
    crTargetQuantBitsConfig: Int,
    downFactorConfig: Int,
    // Parameters to define the order of the three main processing steps
    op1Type: ProcessingStep.Type,
    op2Type: ProcessingStep.Type,
    op3Type: ProcessingStep.Type
) extends Module {

  private val reorderableOps = Seq(ProcessingStep.SpatialSampling, ProcessingStep.ColorQuantization, ProcessingStep.ChromaSubsampling)
  require(reorderableOps.contains(op1Type), "op1Type must be a valid reorderable operation.")
  require(reorderableOps.contains(op2Type), "op2Type must be a valid reorderable operation.")
  require(reorderableOps.contains(op3Type), "op3Type must be a valid reorderable operation.")
  require(Seq(op1Type, op2Type, op3Type).distinct.size == 3, "op1, op2, and op3 types must be distinct and form a permutation.")

  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new PixelBundle()))
    val out = Decoupled(new PixelYCbCrBundle())
    val sof = Input(Bool())
    val eol = Input(Bool())
  })

  val fixedInputBitWidth = 8

  // Instantiate all sub-modules
  val toYC    = Module(new RGB2YCbCr())
  val spatial = Module(new SpatialDownsampler(width, height, downFactorConfig))
  val quant   = Module(new ColorQuantizer(
    yTargetBits = yTargetQuantBitsConfig,
    cbTargetBits = cbTargetQuantBitsConfig,
    crTargetBits = crTargetQuantBitsConfig,
    originalBitWidth = fixedInputBitWidth
  ))
  // Instantiate ChromaSubsampler with new parameters
  val chroma  = Module(new ChromaSubsampler(
    imageWidth = width, 
    imageHeight = height,
    bitWidth = fixedInputBitWidth,
    param_a = chroma_param_a_config, 
    param_b = chroma_param_b_config  
  ))

  spatial.io.sof := io.sof 
  spatial.io.eol := io.eol

  val q_after_toYC = Module(new Queue(new PixelYCbCrBundle(), 1))
  val q_after_op1 = Module(new Queue(new PixelYCbCrBundle(), 1))
  val q_after_op2 = Module(new Queue(new PixelYCbCrBundle(), 1))

  toYC.io.in.valid := false.B; toYC.io.in.bits := DontCare; io.in.ready := false.B 
  toYC.io.out.ready := false.B 

  spatial.io.in.valid := false.B; spatial.io.in.bits := DontCare; spatial.io.out.ready := false.B
  quant.io.in.valid := false.B; quant.io.in.bits := DontCare; quant.io.out.ready := false.B
  chroma.io.dataIn.valid := false.B; chroma.io.dataIn.bits := DontCare; chroma.io.dataOut.ready := false.B
  
  q_after_toYC.io.enq.valid := false.B; q_after_toYC.io.enq.bits := DontCare; q_after_toYC.io.deq.ready := false.B
  q_after_op1.io.enq.valid := false.B; q_after_op1.io.enq.bits := DontCare; q_after_op1.io.deq.ready := false.B
  q_after_op2.io.enq.valid := false.B; q_after_op2.io.enq.bits := DontCare; q_after_op2.io.deq.ready := false.B

  io.out.valid := false.B; io.out.bits := DontCare 

  toYC.io.in <> io.in
  toYC.io.out <> q_after_toYC.io.enq

  when(op1Type === ProcessingStep.SpatialSampling) {
    spatial.io.in <> q_after_toYC.io.deq
    spatial.io.out <> q_after_op1.io.enq
  } .elsewhen(op1Type === ProcessingStep.ColorQuantization) {
    quant.io.in <> q_after_toYC.io.deq
    quant.io.out <> q_after_op1.io.enq
  } .elsewhen(op1Type === ProcessingStep.ChromaSubsampling) {
    chroma.io.dataIn <> q_after_toYC.io.deq
    chroma.io.dataOut <> q_after_op1.io.enq
  }

  when(op2Type === ProcessingStep.SpatialSampling) {
    spatial.io.in <> q_after_op1.io.deq
    spatial.io.out <> q_after_op2.io.enq
  } .elsewhen(op2Type === ProcessingStep.ColorQuantization) {
    quant.io.in <> q_after_op1.io.deq
    quant.io.out <> q_after_op2.io.enq
  } .elsewhen(op2Type === ProcessingStep.ChromaSubsampling) {
    chroma.io.dataIn <> q_after_op1.io.deq
    chroma.io.dataOut <> q_after_op2.io.enq
  }

  when(op3Type === ProcessingStep.SpatialSampling) {
    spatial.io.in <> q_after_op2.io.deq
    spatial.io.out <> io.out 
  } .elsewhen(op3Type === ProcessingStep.ColorQuantization) {
    quant.io.in <> q_after_op2.io.deq
    quant.io.out <> io.out
  } .elsewhen(op3Type === ProcessingStep.ChromaSubsampling) {
    chroma.io.dataIn <> q_after_op2.io.deq
    chroma.io.dataOut <> io.out
  }
}
