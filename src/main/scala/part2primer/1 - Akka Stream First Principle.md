# `Akka` Stream First Principle

> ***The goal of this course is to learn how to build -***
>
> 1. **Asynchronous**
> 2. **Back-pressured**
> 3. **Incremental**
> 4. **Potentially infinite `Data Processing` systems**
>
> known as - ***Reactive Streams***

## 0. Reactive Streams

Some key concepts :

* **`publisher` - emits element (asynchronously)**
* **`subscriber` - receives elements**
* **`processor` - transforms elements along the way**
* **`asynchronous`**
* **`back-pressure`**

> ***Reactive Streams*** is an `SPI (service provider interface)`, not an `API (application provider interface)`
>
> Our focus - `AKKA Streams API`

## 1. `Akka Stream`

We build streams by connecting following components :

#### Source = `publisher`

It emits elements asynchronously.

* It may or may not terminate

#### Sink = `subscriber`

It receives elements.

* It shall terminate only when the publisher terminates

#### Flow = `processor`

It transforms elements

#### Directions

And we also need to identify "the directions" of the stream:

* `Upstream` = to the ***source***
* `Downstream` = to the ***sink***

![image-20220604170943796](E:/TyporaFile/Picbed/image-20220604170943796.png)