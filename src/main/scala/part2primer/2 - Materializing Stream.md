# Materializing Stream

> Goal -***Getting a meaningful value out of a running stream.***

## 1. Materializing

Components are static until they run

```scala
val graph = source.via(flow).to(sink)

val result = graph.run() // materialized value
```

> ***A graph is a "blueprint" for a stream.***

Running a graph, which is - "Materializing", allocates the right resources.

* actors, thread pools
* sockets, connections
* etcetera - everything is ***transparent***

## 2. Materialized Values

To ***Materializing a graph = materializing all components*** -

* each component produces a materialized value when run
* the graph produces a ***single*** materialized value
* our job to choose which one to pick

A component can also materialize multiple times -

* we can reuse the same component in different graphs
* different runs = different materializations

> ***A materialized value can be ANYTHING !!!***

