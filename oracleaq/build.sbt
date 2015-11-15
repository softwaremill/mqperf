import AssemblyKeys._

assemblySettings

assemblyOption in assembly ~= { _.copy(includeBin = true, includeScala = false, includeDependency = false) }

mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) => {
  case PathList(ps@_*) if ps.last == "HornetQUtilBundle_$bundle.class" => MergeStrategy.first
  case x => old(x)
}
}