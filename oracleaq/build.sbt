import AssemblyKeys._

assemblySettings

assemblyOption in assembly ~= { _.copy(includeBin = true, includeScala = false, includeDependency = false) }
