package test
import scala.math._
import chisel3._
import chisel3.util._
import chisel3.experimental.chiselName

@chiselName
class FrameTrigger(width: Int = 8, chunkSize: Int = 3, delay: Int = 15) extends Module {
  val io = IO(new Bundle {
    val in = Input(new Bundle {
      val data = UInt(width.W)
      val clear = Bool()
    })
    val out = Output(new Bundle {
      val trigger = Bool()
    })
  })

  val buf = RegInit(VecInit(for { a <- 0 to chunkSize } yield 127.U(width.W)))
  val cnt = RegInit(0.U(8.W))
  // Chiro: -127 ~ 127
  val threshold = (127.toDouble / (sqrt(2) * 2)).toInt.U
  val ave = buf
    .map(x =>
      (Mux(x < 127.U, 127.U - x, x - 127.U)).asTypeOf(UInt((width + 2).W))
    )
    .reduce(_ + _)
  val run = RegInit(true.B)

  val triggerDelay = RegInit(0.U(8.W))
  io.out.trigger := triggerDelay =/= 0.U
  when(io.in.clear || run) {
    when(cnt =/= (chunkSize - 1).U) {
      cnt := cnt + 1.U
    }.otherwise {
      cnt := 0.U
    }
    when(ave >= threshold) {
      triggerDelay := delay.U
      run := false.B
    }.otherwise {
      triggerDelay := Mux(triggerDelay === 0.U, 0.U, triggerDelay - 1.U)
    }

  }
  when(io.in.clear) {
    run := true.B
  }
  buf(cnt) := io.in.data
}

@chiselName
class FrameTriggerWrapper(width: Int = 8, chunkSize: Int = 3)
    extends RawModule {
  val io = IO(new Bundle {
    val in = Input(new Bundle {
      val data = UInt(width.W)
      val clear = Bool()
    })
    val out = Output(new Bundle {
      val trigger = Bool()
    })
    val clock = Input(Clock())
    val resetN = Input(Bool())
  })
  withClockAndReset(io.clock, ~io.resetN) {
    val module = Module(new FrameTrigger(width = width, chunkSize = chunkSize))
    module.io.in <> io.in
    module.io.out <> io.out
  }
}
