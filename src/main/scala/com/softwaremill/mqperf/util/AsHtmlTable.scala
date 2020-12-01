package com.softwaremill.mqperf.util

object AsHtmlTable extends App {
  val data = """
    |1	1	2	13 647,00	13 648,00	48,00	44,00
    |5	1	2	36 035,00	36 021,00	47,00	46,00
    |25	1	2	43 643,00	43 630,00	48,00	48,00
    |
    |1	2	4	21 380,00	21 379,00	47,00	45,00
    |5	2	4	39 320,00	39 316,00	48,00	47,00
    |25	2	4	51 089,00	51 090,00	48,00	48,00
    |
    |1	4	8	35 538,00	35 525,00	47,00	46,00
    |5	4	8	42 881,00	42 882,00	48,00	47,00
    |25	4	8	52 820,00	52 826,00	49,00	48,00
    |
    |5	6	12	44 435,00	44 436,00	48,00	48,00
    |25	6	12	49 279,00	49 278,00	77,00	48,00
    """.stripMargin

  println("""<table>
            |  <thead>
            |    <tr>
            |      <th>Threads</th>
            |      <th>Sender nodes</th>
            |      <th>Receiver nodes</th>
            |      <th>Send msgs/s</th>
            |      <th>Receive msgs/s</th>
            |      <th>Processing latency</th>
            |      <th>Send latency</th>
            |    </tr>
            |  </thead>
            |  <tbody>""".stripMargin)
  data.split("\n").foreach { line =>
    if (line.trim().nonEmpty) {
      println("    <tr>")
      line.split("\t").toList.foreach(v => println(s"      <td>$v</td>"))
      println("    </tr>")
    }
  }
  println("""  </tbody>
            |</table>
            |""".stripMargin)
}
