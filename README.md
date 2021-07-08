# scalapb-playjson [![Build Status](https://travis-ci.com/scalapb-json/scalapb-playjson.svg?branch=master)](https://travis-ci.com/scalapb-json/scalapb-playjson)
[![scaladoc](https://javadoc-badge.appspot.com/io.github.scalapb-json/scalapb-playjson_2.12.svg?label=scaladoc)](https://javadoc-badge.appspot.com/io.github.scalapb-json/scalapb-playjson_2.12/scalapb_playjson/index.html?javadocio=true)

The structure of this project is hugely inspired by [scalapb-json4s](https://github.com/scalapb/scalapb-json4s)

## Dependency

Include in your `build.sbt` file

### core

```scala
libraryDependencies += "io.github.scalapb-json" %% "scalapb-playjson" % "0.15.1"
```

for scala-js

```scala
libraryDependencies += "io.github.scalapb-json" %%% "scalapb-playjson" % "0.15.1"
```

### macros

```scala
libraryDependencies += "io.github.scalapb-json" %% "scalapb-playjson-macros" % "0.15.1"
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
