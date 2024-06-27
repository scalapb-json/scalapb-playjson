# scalapb-playjson
[![scaladoc](https://javadoc.io/badge2/io.github.scalapb-json/scalapb-playjson_2.13/javadoc.svg)](https://javadoc.io/doc/io.github.scalapb-json/scalapb-playjson_2.13/latest/scalapb_playjson/index.html)

The structure of this project is hugely inspired by [scalapb-json4s](https://github.com/scalapb/scalapb-json4s)

## Dependency

Include in your `build.sbt` file

### core

```scala
libraryDependencies += "io.github.scalapb-json" %% "scalapb-playjson" % "0.18.0"
```

for scala-js or scala-native

```scala
libraryDependencies += "io.github.scalapb-json" %%% "scalapb-playjson" % "0.18.0"
```

### macros

```scala
libraryDependencies += "io.github.scalapb-json" %% "scalapb-playjson-macros" % "0.18.0"
```

## Usage

There are four functions you can use directly to serialize/deserialize your messages:

```scala
JsonFormat.toJsonString(msg) // returns String
JsonFormat.toJson(msg) // returns JsObject

JsonFormat.fromJsonString(str) // return MessageType
JsonFormat.fromJson(json) // return MessageType
```

Or you can use Reads/Writes/Format implicitly:
```scala
implicit val myMsgWrites: Writes[MyMsg] = JsonFormat.protoToWriter[MyMsg]

implicit val myMsgReads: Reads[MyMsg] = JsonFormat.protoToReads[MyMsg]

implicit val myMsgFormat: Format[MyMsg] = JsonFormat.protoToFormat[MyMsg]
```

### Credits

fork from https://github.com/whisklabs/scalapb-playjson
